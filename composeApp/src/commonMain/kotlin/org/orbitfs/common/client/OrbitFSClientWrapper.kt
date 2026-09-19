package org.orbitfs.common.client

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.orbitfs.client.NetworkTransportClient
import org.orbitfs.client.CachingOrbitFSClient
import org.orbitfs.common.data.ConnectionConfig
import org.orbitfs.common.model.FileInfo
import org.orbitfs.common.util.OrbitLogger
import java.io.OutputStream

class OrbitFSClientWrapper(private val config: ConnectionConfig) {
    private val TAG = "OrbitFSClient"
    private var client: CachingOrbitFSClient? = null

    suspend fun connect() = withContext(Dispatchers.IO) {
        OrbitLogger.d(TAG, "Connecting to ${config.host}:${config.port}...")
        val t = NetworkTransportClient(config.host, config.port, config.socketTimeoutMs.toLong())
        t.connect()
        client = CachingOrbitFSClient(t)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        client?.close()
        client = null
    }

    private fun ensureConnected(): CachingOrbitFSClient {
        return client ?: throw IllegalStateException("Client not connected to ${config.host}")
    }

    /**
     * Normalizes the path for the backend RPC.
     * The server jail expects paths relative to its root (no leading slash).
     * The root is represented by an empty string or "."
     */
    private fun preparePath(path: String): String {
        val trimmed = path.trim().trimStart('/').trimEnd('/')
        return if (trimmed == "." || trimmed.isEmpty()) "" else trimmed
    }

    suspend fun list(path: String, showHidden: Boolean): List<FileInfo> = withContext(Dispatchers.IO) {
        val c = ensureConnected()
        val serverPath = preparePath(path)
        OrbitLogger.d(TAG, "RPC LIST Request -> serverPath: '$serverPath' (uiPath: '$path')")
        
        try {
            val entries = c.listWithStat(serverPath, showHidden)
            val normalizedPrefix = if (serverPath.isEmpty()) "" else "/$serverPath"
            
            entries.map { entry ->
                val entryName = entry.name()
                val entryPath = if (normalizedPrefix.isEmpty()) "/$entryName" else "$normalizedPrefix/$entryName"
                
                FileInfo(
                    name = entryName,
                    path = entryPath,
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
            OrbitLogger.e(TAG, "RPC LIST FAILED for $serverPath", e)
            throw e
        }
    }

    suspend fun readFile(path: String): ByteArray = withContext(Dispatchers.IO) {
        val c = ensureConnected()
        val serverPath = preparePath(path)
        val handle = c.open(serverPath)
        try {
            val stat = c.stat(handle)
            val size = stat.size().toInt()
            
            if (size <= 0) return@withContext byteArrayOf()
            
            c.read(handle, 0, size)
        } finally {
            c.close(handle)
        }
    }

    suspend fun streamFile(path: String, output: OutputStream, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val c = ensureConnected()
        val serverPath = preparePath(path)
        val handle = c.open(serverPath)
        try {
            val stat = c.stat(handle)
            val totalSize = stat.size()
            val bufferSize = config.chunkSizeKb * 1024
            var offset = 0L
            
            while (offset < totalSize) {
                val count = minOf(bufferSize.toLong(), totalSize - offset).toInt()
                val chunk = c.read(handle, offset, count)
                if (chunk.isEmpty()) break
                output.write(chunk)
                offset += chunk.size
                onProgress(offset, totalSize)
            }
            output.flush()
        } finally {
            c.close(handle)
        }
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        ensureConnected().delete(preparePath(path))
    }

    suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastError: Exception? = null
        repeat(2) {
            try {
                return block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastError = e
                try {
                    disconnect()
                    connect()
                } catch (ce: Exception) {
                    OrbitLogger.e(TAG, "Retry reconnect failed", ce)
                }
            }
        }
        throw lastError ?: RuntimeException("RPC request failed")
    }
}
