package org.orbitfs.common.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.orbitfs.client.NetworkTransportClient
import org.orbitfs.client.CachingOrbitFSClient
import org.orbitfs.common.data.ConnectionConfig
import org.orbitfs.common.model.FileInfo
import org.orbitfs.common.util.OrbitLogger
import java.io.IOException
import java.io.OutputStream

class OrbitFSClientWrapper(private val config: ConnectionConfig) {
    private val TAG = "OrbitFSClient"
    private var client: CachingOrbitFSClient? = null

    suspend fun connect() = withContext(Dispatchers.IO) {
        OrbitLogger.d(TAG, "Connecting to ${config.host}:${config.port}")
        val t = NetworkTransportClient(config.host, config.port, config.socketTimeoutMs.toLong())
        t.connect()
        client = CachingOrbitFSClient(t)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        client?.close()
        client = null
    }

    private fun ensureConnected(): CachingOrbitFSClient {
        return client ?: throw IllegalStateException("Client not connected")
    }

    /**
     * Normalizes the path for the backend RPC.
     * The UI uses absolute paths (e.g. "/Folder") where "/" is the root of the remote share.
     * We send relative paths to the server for sandboxed resolution.
     */
    private fun preparePath(path: String): String {
        return path.trimStart('/').replace("//", "/")
    }

    suspend fun list(path: String, showHidden: Boolean): List<FileInfo> = withContext(Dispatchers.IO) {
        val c = ensureConnected()
        val serverPath = preparePath(path)
        OrbitLogger.d(TAG, "LIST -> serverPath: '$serverPath' (uiPath: '$path')")
        
        try {
            val entries = c.listWithStat(serverPath, showHidden)
            
            entries.map { entry ->
                val entryName = entry.name()
                
                // Construct the UI Path relative to the share root "/"
                val uiBase = if (path == "/" || path.isEmpty()) "" else path.removeSuffix("/")
                val entryPath = "$uiBase/$entryName"
                
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
            OrbitLogger.e(TAG, "LIST FAILED for $serverPath", e)
            throw e
        }
    }

    suspend fun readFile(path: String): ByteArray = withContext(Dispatchers.IO) {
        val c = ensureConnected()
        val serverPath = preparePath(path)
        val stat = c.stat(serverPath)
        val size = stat.size()
        
        if (size <= 0L) return@withContext byteArrayOf()
        if (size > 50 * 1024 * 1024) {
            throw IllegalArgumentException("File too large to read into memory (${size / (1024 * 1024)}MB)")
        }
        
        val handle = c.open(serverPath)
        try {
            c.read(handle, 0, size.toInt())
        } finally {
            c.close(handle)
        }
    }

    suspend fun streamFile(path: String, output: OutputStream, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val c = ensureConnected()
        val serverPath = preparePath(path)
        val stat = c.stat(serverPath)
        val totalSize = stat.size()
        val bufferSize = config.chunkSizeKb * 1024
        var offset = 0L
        
        val handle = c.open(serverPath)
        try {
            while (offset < totalSize) {
                val count = minOf(bufferSize.toLong(), totalSize - offset).toInt()
                val chunk = c.read(handle, offset, count)
                if (chunk.isEmpty()) {
                    throw IOException("Stream error: Received 0 bytes from server at offset $offset / $totalSize")
                }
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
            try { return block() } catch (e: Exception) {
                lastError = e
                try { connect() } catch (_: Exception) {}
            }
        }
        throw lastError ?: RuntimeException("RPC failed")
    }
}
