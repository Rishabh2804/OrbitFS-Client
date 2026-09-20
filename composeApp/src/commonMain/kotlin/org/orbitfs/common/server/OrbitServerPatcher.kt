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
 * Fixes the "Path Doubling" bug by intelligently distinguishing absolute handles.
 */
object OrbitServerPatcher {

    private fun log(msg: String) {
        println("[ORBIT-SERVER-DEBUG] $msg")
    }

    /**
     * Resilient path resolution.
     * Fixes doubling by strictly checking if the input is already an absolute path.
     */
    fun resolveSafely(sandbox: SandboxGuard, rawPath: String?): Path {
        val input = rawPath?.trim() ?: ""
        if (input.isEmpty() || input == "/" || input == ".") return sandbox.root

        val rootStr = sandbox.root.toString()

        // 1. If it's already an absolute path and STARTS with root, return it immediately.
        // This is the CRITICAL FIX for the doubling bug observed in logs.
        if (input.startsWith(rootStr)) {
             return Paths.get(input).normalize()
        }

        // 2. Otherwise treat as a relative request from the UI.
        val relative = input.trimStart('/')
        val resolved = sandbox.root.resolve(relative).normalize()
        
        if (!resolved.startsWith(sandbox.root)) {
            log("SECURITY: Blocked escape attempt for path: '$rawPath'")
            throw SecurityException("Access outside sandbox forbidden")
        }
        return resolved
    }

    fun patch(server: OrbitServerImpl, sandbox: SandboxGuard, hideHidden: Boolean) {
        log("Hardening server handlers at root: ${sandbox.root}")
        
        val handlersField = OrbitServerImpl::class.java.getDeclaredField("handlers")
        handlersField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val handlers = handlersField.get(server) as MutableMap<RpcMethod, BiFunction<RPCRequest, StorageEngine, RPCResponse>>

        // PING
        handlers[RpcMethod.PING] = BiFunction { req, _ ->
            RPCResponse(req.requestId(), RPCStatus.OK, 0, 0, null, "PONG", null, null)
        }

        // OPEN
        handlers[RpcMethod.OPEN] = BiFunction { request, engine ->
            val path = request.path()
            log("REQ: OPEN '$path'")
            try {
                val target = resolveSafely(sandbox, path)
                if (Files.isDirectory(target)) {
                    log("RES: OPEN DIR SUCCESS -> $target")
                    RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, target.toString(), null, null, null)
                } else {
                    try {
                        val fd = engine.open(target.toString())
                        log("RES: OPEN CORE FD SUCCESS -> $fd")
                        RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, fd, null, null, null)
                    } catch (e: Exception) {
                        log("RES: OPEN CORE FAILED (${e.message}). Using PATH as handle.")
                        RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, target.toString(), null, null, null)
                    }
                }
            } catch (e: Exception) {
                log("RES: OPEN ERROR -> ${e.message}")
                RPCResponse.error(request.requestId(), 1)
            }
        }

        // CLOSE
        handlers[RpcMethod.CLOSE] = BiFunction { request, engine ->
            val fd = request.fd()
            try {
                if (!fd.isNullOrEmpty() && !fd.startsWith("/") && !fd.contains(File.separator)) {
                    engine.close(fd)
                }
                RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, null, null, null, null)
            } catch (e: Exception) {
                RPCResponse.error(request.requestId(), 1)
            }
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
                log("RES: LIST SUCCESS -> Found ${entries.size} items")
                RPCResponse(
                    request.requestId(), RPCStatus.OK, 0, 0, null, null, null,
                    entries.sortedWith(compareByDescending<RPCResponse.RPCEntry> { it.isDir() }.thenBy { it.name().lowercase() })
                )
            } catch (e: Exception) {
                log("RES: LIST ERROR -> ${e.message}")
                RPCResponse.error(request.requestId(), 1)
            }
        }

        // READ
        handlers[RpcMethod.READ] = BiFunction { request, engine ->
            val fd = request.fd()
            val offset = request.offset()
            val count = request.count()
            log("REQ: READ FD='$fd' OFFSET=$offset COUNT=$count")
            try {
                val bytes: ByteArray = if (!fd.isNullOrEmpty() && (fd.startsWith("/") || fd.contains(File.separator))) {
                    readDirectly(sandbox, fd, offset, count)
                } else if (!fd.isNullOrEmpty()) {
                    try {
                        engine.read(fd, offset, count)
                    } catch (e: Exception) {
                        log("RES: READ CORE FAILED. Trying path fallback.")
                        readDirectly(sandbox, fd, offset, count)
                    }
                } else {
                    readDirectly(sandbox, request.path(), offset, count)
                }

                log("RES: READ SUCCESS -> Sent ${bytes.size} bytes")
                val encoded = Base64.getEncoder().encodeToString(bytes)
                RPCResponse(request.requestId(), RPCStatus.OK, 0, bytes.size.toLong(), null, encoded, null, null)
            } catch (e: Exception) {
                log("RES: READ ERROR -> ${e.message}")
                RPCResponse.error(request.requestId(), 1)
            }
        }

        // WRITE
        handlers[RpcMethod.WRITE] = BiFunction { request, engine ->
            val fd = request.fd()
            val b64 = request.dataBase64()
            val data = if (b64 != null) Base64.getDecoder().decode(b64) else byteArrayOf()
            log("REQ: WRITE FD='$fd' BYTES=${data.size}")
            try {
                if (!fd.isNullOrEmpty() && (fd.startsWith("/") || fd.contains(File.separator))) {
                    val target = resolveSafely(sandbox, fd)
                    RandomAccessFile(target.toFile(), "rw").use { raf ->
                        raf.seek(request.offset())
                        raf.write(data)
                    }
                } else if (!fd.isNullOrEmpty()) {
                    engine.write(fd, request.offset(), data)
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
        if (!Files.exists(target) || Files.isDirectory(target)) {
            log("DIRECT READ FAILED: Not a file: $target")
            return byteArrayOf()
        }
        
        return try {
            RandomAccessFile(target.toFile(), "r").use { raf ->
                raf.seek(offset)
                val len = minOf(count.toLong(), raf.length() - offset).coerceAtLeast(0).toInt()
                val buf = ByteArray(len)
                val read = raf.read(buf)
                if (read <= 0) byteArrayOf() else if (read < len) buf.copyOf(read) else buf
            }
        } catch (e: Exception) {
            log("DIRECT READ FATAL ERROR: ${e.message}")
            byteArrayOf()
        }
    }
}
