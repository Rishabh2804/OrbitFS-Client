package org.orbitfs.common.server

import org.orbitfs.common.protocol.*
import org.orbitfs.server.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.function.BiFunction
import kotlin.io.path.*

/**
 * Aggressive RPC Handler Patcher for OrbitServerImpl.
 * 
 * Provides robust implementations for ALL major operations.
 * Fixes Path Doubling and "Server Code 1" errors.
 */
object OrbitServerPatcher {

    private fun log(msg: String) {
        println("[ORBIT-SERVER-DEBUG] $msg")
    }

    /**
     * Resilient path resolution.
     * Prevents doubling by strictly checking if the input is already an absolute path.
     */
    fun resolveSafely(sandbox: SandboxGuard, rawPath: String?): Path {
        val input = rawPath?.trim() ?: ""
        if (input.isEmpty() || input == "/" || input == ".") return sandbox.root

        val rootStr = sandbox.root.toString()

        // 1. Check for Absolute Paths (Fixes Doubling)
        // If it already contains the root string, it's an absolute handle from the server logic.
        if (input.startsWith(rootStr) || input.contains(rootStr)) {
             try {
                 val candidate = Paths.get(input).normalize()
                 if (candidate.startsWith(sandbox.root)) return candidate
             } catch (_: Exception) {}
        }

        // 2. Relative Resolution (UI requests)
        val relative = input.trimStart('/')
        val resolved = sandbox.root.resolve(relative).normalize()
        
        if (!resolved.startsWith(sandbox.root)) {
            log("SECURITY: Path '$rawPath' resolved OUTSIDE root '${sandbox.root}'")
            throw SecurityException("Access outside sandbox forbidden")
        }
        return resolved
    }

    fun patch(server: OrbitServerImpl, sandbox: SandboxGuard, hideHidden: Boolean) {
        log("Hardening server handlers. Root: ${sandbox.root}")
        
        val handlersField = OrbitServerImpl::class.java.getDeclaredField("handlers")
        handlersField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val handlers = handlersField.get(server) as MutableMap<RpcMethod, BiFunction<RPCRequest, StorageEngine, RPCResponse>>

        // PING
        handlers[RpcMethod.PING] = BiFunction { req, _ ->
            RPCResponse(req.requestId(), RPCStatus.OK, 0, 0, null, "PONG", null, null)
        }

        // OPEN -> Simplified to always return absolute path as handle
        handlers[RpcMethod.OPEN] = BiFunction { request, _ ->
            val path = request.path()
            log("REQ: OPEN '$path'")
            try {
                val target = resolveSafely(sandbox, path)
                log("RES: OPEN SUCCESS -> Handle: $target")
                RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, target.toString(), null, null, null)
            } catch (e: Exception) {
                log("RES: OPEN ERROR -> ${e.message}")
                RPCResponse.error(request.requestId(), 1)
            }
        }

        // CLOSE -> NOP for path handles
        handlers[RpcMethod.CLOSE] = BiFunction { request, _ ->
            RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, null, null, null, null)
        }

        // LIST
        handlers[RpcMethod.LIST] = BiFunction { request, _ ->
            val raw = if (!request.fd().isNullOrEmpty()) request.fd() else request.path()
            log("REQ: LIST '$raw'")
            try {
                val target = resolveSafely(sandbox, raw)
                if (!Files.exists(target) || !Files.isDirectory(target)) {
                    return@BiFunction RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, null, null, null, emptyList())
                }
                
                val showHidden = request.showHidden() ?: (!hideHidden)
                val entries = Files.list(target).use { stream ->
                    stream.iterator().asSequence()
                        .filter { showHidden || !it.name.startsWith(".") }
                        .mapNotNull { path ->
                            try {
                                val isDir = path.isDirectory()
                                RPCResponse.RPCEntry(
                                    path.name,
                                    isDir,
                                    if (isDir) 0L else path.fileSize(),
                                    path.getLastModifiedTime().toMillis(),
                                    if (isDir) "" else path.extension,
                                    if (isDir) "inode/directory" else "application/octet-stream", 
                                    "0644", "orbit"
                                )
                            } catch (e: Exception) { null }
                        }
                        .toList()
                }
                RPCResponse(
                    request.requestId(), RPCStatus.OK, 0, 0, null, null, null,
                    entries.sortedWith(compareByDescending<RPCResponse.RPCEntry> { it.isDir() }.thenBy { it.name().lowercase() })
                )
            } catch (e: Exception) {
                log("RES: LIST ERROR -> ${e.message}")
                RPCResponse.error(request.requestId(), 1)
            }
        }

        // READ -> Direct file reading only
        handlers[RpcMethod.READ] = BiFunction { request, _ ->
            val fd = request.fd()
            val offset = request.offset()
            val count = request.count()
            log("REQ: READ Handle='$fd' OFFSET=$offset COUNT=$count")
            try {
                val bytes = readDirectly(sandbox, fd, offset, count)
                log("RES: READ SUCCESS -> ${bytes.size} bytes")
                val encoded = Base64.getEncoder().encodeToString(bytes)
                RPCResponse(request.requestId(), RPCStatus.OK, 0, bytes.size.toLong(), null, encoded, null, null)
            } catch (e: Exception) {
                log("RES: READ ERROR -> ${e.message}")
                RPCResponse.error(request.requestId(), 1)
            }
        }

        // WRITE
        handlers[RpcMethod.WRITE] = BiFunction { request, _ ->
            val fd = request.fd()
            val b64 = request.dataBase64()
            val data = if (b64 != null) Base64.getDecoder().decode(b64) else byteArrayOf()
            log("REQ: WRITE Handle='$fd' BYTES=${data.size}")
            try {
                val target = resolveSafely(sandbox, fd)
                RandomAccessFile(target.toFile(), "rw").use { raf ->
                    raf.seek(request.offset())
                    raf.write(data)
                }
                RPCResponse(request.requestId(), RPCStatus.OK, 0, data.size.toLong(), null, null, null, null)
            } catch (e: Exception) {
                log("RES: WRITE ERROR -> ${e.message}")
                RPCResponse.error(request.requestId(), 1)
            }
        }

        // DELETE
        handlers[RpcMethod.DELETE] = BiFunction { request, _ ->
            val path = request.path()
            log("REQ: DELETE '$path'")
            try {
                val target = resolveSafely(sandbox, path)
                val deleted = Files.deleteIfExists(target)
                log("RES: DELETE SUCCESS -> $deleted")
                RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, null, null, null, null)
            } catch (e: Exception) {
                log("RES: DELETE ERROR -> ${e.message}")
                RPCResponse.error(request.requestId(), 1)
            }
        }

        // STAT
        handlers[RpcMethod.STAT] = BiFunction { request, _ ->
            val raw = if (!request.fd().isNullOrEmpty()) request.fd() else request.path()
            log("REQ: STAT '$raw'")
            try {
                val target = resolveSafely(sandbox, raw)
                if (!Files.exists(target)) return@BiFunction RPCResponse.error(request.requestId(), 1)
                val isDir = Files.isDirectory(target)
                val stat = RPCResponse.RPCStat(
                    if (isDir) 0L else Files.size(target),
                    isDir,
                    Files.getLastModifiedTime(target).toMillis(),
                    Files.getLastModifiedTime(target).toMillis(),
                    if (isDir) "" else target.extension,
                    "application/octet-stream",
                    "0644", "orbit"
                )
                RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, null, null, stat, null)
            } catch (e: Exception) {
                log("RES: STAT ERROR -> ${e.message}")
                RPCResponse.error(request.requestId(), 1)
            }
        }
    }

    private fun readDirectly(sandbox: SandboxGuard, raw: String?, offset: Long, count: Int): ByteArray {
        val target = try { resolveSafely(sandbox, raw) } catch (_: Exception) { return byteArrayOf() }
        if (!Files.exists(target) || Files.isDirectory(target)) return byteArrayOf()
        
        return try {
            RandomAccessFile(target.toFile(), "r").use { raf ->
                val fileSize = raf.length()
                if (offset >= fileSize) return byteArrayOf()
                raf.seek(offset)
                val len = minOf(count.toLong(), fileSize - offset).coerceAtLeast(0).toInt()
                if (len <= 0) return byteArrayOf()
                val buf = ByteArray(len)
                val read = raf.read(buf)
                if (read <= 0) byteArrayOf() else if (read < len) buf.copyOf(read) else buf
            }
        } catch (e: Exception) {
            log("DIRECT READ FATAL: ${e.message}")
            byteArrayOf()
        }
    }
}
