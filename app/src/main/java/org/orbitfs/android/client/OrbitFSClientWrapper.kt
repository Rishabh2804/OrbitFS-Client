package org.orbitfs.android.client

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.orbitfs.client.CachingOrbitFSClient
import org.orbitfs.client.NetworkTransportClient
import org.orbitfs.client.OrbitFSClient as JavaOrbitFSClient
import org.orbitfs.android.model.FileInfo
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TAG = "OrbitFSClientWrapper"

class OrbitFSClientWrapper(
    private val host: String,
    private val port: Int,
    private val timeoutMs: Long = 10_000L,
    private val chunkSize: Int = 64 * 1024
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

                Log.d(TAG, "Connected to $host:$port")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed", e)
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
                Log.w(TAG, "Error during disconnect", e)
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
                lastModified = stat.lastModifiedMillis()
            )
        } catch (e: Exception) {
            throw IOException("stat failed: ${e.message}", e)
        } finally {
            closeHandle(handle)
        }
    }

    suspend fun list(path: String): List<FileInfo> = withContext(Dispatchers.IO) {
        val handle = openHandle(path)
        try {
            val entries = requireClient().list(handle)
            entries.map { entry ->
                val fullPath = if (path.endsWith("/")) "$path$entry" else "$path/$entry"
                var statResult: org.orbitfs.common.model.FileStat? = null
                try {
                    val entryHandle = openHandle(fullPath)
                    try {
                        statResult = requireClient().stat(entryHandle)
                    } finally {
                        closeHandle(entryHandle)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to stat entry: $fullPath", e)
                }
                FileInfo(
                    name = entry,
                    path = fullPath,
                    size = statResult?.size() ?: 0L,
                    isDirectory = statResult?.isDirectory() ?: false,
                    lastModified = statResult?.lastModifiedMillis() ?: 0L
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "list failed for $path", e)
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
            Log.w(TAG, "Failed to close handle", e)
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
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).coerceAtMost(maxDelay)
            }
        }
        throw IllegalStateException("Unreachable: retry loop exhausted")
    }
}
