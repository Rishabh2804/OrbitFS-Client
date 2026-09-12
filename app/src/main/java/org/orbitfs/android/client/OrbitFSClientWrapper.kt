package org.orbitfs.android.client

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.orbitfs.client.CachingOrbitFSClient
import org.orbitfs.client.NetworkTransportClient
import org.orbitfs.client.OrbitFSClient as JavaOrbitFSClient
import org.orbitfs.android.model.FileInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import timber.log.Timber

class OrbitFSClientWrapper(
    private val host: String,
    private val port: Int,
    private val timeoutMs: Long = 10_000L,
    private val chunkSize: Int = 256 * 1024
) {

    private var transport: NetworkTransportClient? = null
    private var cachingClient: CachingOrbitFSClient? = null
    private var isOpen = false
    private val lock = Mutex()

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                if (isOpen) {
                    return@withContext Result.success(Unit)
                }

                val t = NetworkTransportClient(host, port, timeoutMs)
                t.connect()

                cachingClient = CachingOrbitFSClient(t)
                transport = t
                isOpen = true

                Timber.d("Connected to $host:$port")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Connect failed")
                Result.failure(IOException("Failed to connect to $host:$port: ${e.message}", e))
            }
        }
    }

    suspend fun disconnect() {
        lock.withLock {
            try {
                cachingClient?.close()
                transport?.close()
            } catch (e: Exception) {
                Timber.w(e, "Error during disconnect")
            } finally {
                isOpen = false
                cachingClient = null
                transport = null
            }
        }
    }

    val isConnected: Boolean
        get() = isOpen

    suspend fun ensureConnected() = withContext(Dispatchers.IO) {
        val c = cachingClient ?: throw IOException("Not connected")
    }

    suspend fun stat(path: String): FileInfo = withContext(Dispatchers.IO) {
        val handle = openHandle(path)
        try {
            val stat = requireClient().stat(handle)
            FileInfo(
                name = path.substringAfterLast("/"),
                path = path,
                size = stat.size(),
                isDirectory = stat.isDirectory(),
                lastModified = stat.lastModifiedMillis(),
                created = stat.createdMillis(),
                extension = stat.extension() ?: "",
                mimeType = stat.mimeType() ?: "",
                permissions = stat.permissions() ?: "",
                owner = stat.owner() ?: ""
            )
        } catch (e: Exception) {
            throw IOException("stat failed: ${e.message}", e)
        } finally {
            closeHandle(handle)
        }
    }

    suspend fun list(path: String, showHidden: Boolean = false): List<FileInfo> = withContext(Dispatchers.IO) {
        val handle = openHandle(path)
        try {
            val entries = requireClient().listWithStat(handle, showHidden)
            entries.map { entry ->
                val fullPath = if (path.isEmpty()) entry.name() else if (path.endsWith("/")) "$path${entry.name()}" else "$path/${entry.name()}"
                FileInfo(
                    name = entry.name(),
                    path = fullPath,
                    size = entry.size(),
                    isDirectory = entry.isDir(),
                    lastModified = entry.lastModified(),
                    extension = entry.extension() ?: "",
                    mimeType = entry.mimeType() ?: "",
                    permissions = entry.permissions() ?: "",
                    owner = entry.owner() ?: ""
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "list failed for $path")
            throw IOException("list failed: ${e.message}", e)
        } finally {
            closeHandle(handle)
        }
    }

    suspend fun read(path: String, offset: Long, count: Int): ByteArray = withContext(Dispatchers.IO) {
        val handle = openHandle(path)
        try {
            requireClient().read(handle, offset, count)
        } catch (e: Exception) {
            throw IOException("read failed: ${e.message}", e)
        } finally {
            closeHandle(handle)
        }
    }

    suspend fun readChunk(path: String, chunkIndex: Int): ByteArray {
        val offset = chunkIndex.toLong() * chunkSize
        return read(path, offset, chunkSize)
    }

    suspend fun write(path: String, offset: Long, data: ByteArray): Int = withContext(Dispatchers.IO) {
        val handle = openHandle(path)
        try {
            requireClient().write(handle, offset, data)
        } catch (e: Exception) {
            throw IOException("write failed: ${e.message}", e)
        } finally {
            closeHandle(handle)
        }
    }

    suspend fun readFile(path: String, onProgress: ((bytesRead: Long, totalSize: Long) -> Unit)? = null): ByteArray = withContext(Dispatchers.IO) {
        Timber.d("readFile: path='$path'")
        val handle = openHandle(path)
        Timber.d("readFile: handle=$handle")
        try {
            val stat = requireClient().stat(handle)
            Timber.d("readFile: stat size=${stat.size()}, isDir=${stat.isDirectory()}")
            if (stat.isDirectory()) {
                throw IOException("Cannot read a directory: $path")
            }
            val fileSize = stat.size().toLong()
            if (fileSize == 0L) {
                Timber.d("readFile: zero-byte file")
                return@withContext byteArrayOf()
            }
            val result = ByteArrayOutputStream()
            var offset = 0L
            while (offset < fileSize) {
                val count = minOf(chunkSize.toLong(), fileSize - offset).toInt()
                Timber.d("readFile: requesting read at offset=$offset count=$count")
                val chunk = requireClient().read(handle, offset, count)
                Timber.d("readFile: got ${chunk.size} bytes at offset=$offset")
                if (chunk.isEmpty()) {
                    Timber.w("readFile: empty chunk at offset=$offset, possible EOF reached")
                    break
                }
                result.write(chunk)
                offset += chunk.size
                onProgress?.invoke(offset, fileSize)
            }
            Timber.d("readFile: read ${result.size()} bytes total for '$path'")
            result.toByteArray()
        } catch (e: Exception) {
            Timber.e(e, "readFile failed for '$path'")
            throw IOException("readFile failed: ${e.message}", e)
        } finally {
            closeHandle(handle)
        }
    }

    private fun openHandle(path: String): String {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("Cancelled")
        }
        val c = cachingClient ?: throw IOException("Not connected")
        return c.open(path)
    }

    private fun closeHandle(handle: String) {
        try {
            val c = cachingClient ?: return
            c.close(handle)
        } catch (e: Exception) {
            Timber.w(e, "Failed to close handle")
        }
    }

    @Synchronized
    private fun requireClient(): JavaOrbitFSClient {
        val c = cachingClient ?: throw IOException("Not connected")
        return c
    }

    suspend fun <T> withRetry(
        maxAttempts: Int = 5,
        initialDelay: Duration = 1.seconds,
        maxDelay: Duration = 60.seconds,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == maxAttempts - 1) {
                    throw e
                }
                if (e is IOException && (e.message?.contains("Connection") == true || e.message?.contains("Broken") == true)) {
                    Timber.w(e, "Connection broken, attempting reconnect")
                    lock.withLock {
                        try {
                            cachingClient?.close()
                            transport?.close()
                        } catch (_: Exception) { }
                        isOpen = false
                        cachingClient = null
                        transport = null
                    }
                    connect().getOrElse { 
                        Timber.e(it, "Reconnect failed") 
                        throw it
                    }
                }
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).coerceAtMost(maxDelay)
            }
        }
        throw IllegalStateException("Unreachable: retry loop exhausted")
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val c = cachingClient ?: throw IOException("Not connected")
        c.delete(path)
    }

    suspend fun rename(path: String, newPath: String) = withContext(Dispatchers.IO) {
        val c = cachingClient ?: throw IOException("Not connected")
        c.rename(path, newPath)
    }
}
