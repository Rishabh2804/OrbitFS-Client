package org.orbitfs.common.server

import org.orbitfs.common.protocol.*
import org.orbitfs.server.*
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.function.BiFunction
import kotlin.io.path.*

/**
 * Unified RPC Handler Patcher for OrbitServerImpl.
 * 
 * Provides robust, protocol-compliant implementations for LIST, OPEN, STAT, READ, 
 * DELETE, and PING across both Desktop and Android Satellite modes.
 */
object OrbitServerPatcher {

    fun resolveSafely(sandbox: SandboxGuard, rawPath: String?): Path {
        val p = rawPath?.trim()?.trimEnd('/') ?: ""
        if (p.isEmpty() || p == "/" || p == ".") return sandbox.root

        // If it is already an absolute path that starts with sandbox root
        if (p.startsWith("/")) {
            try {
                val abs = Paths.get(p).normalize()
                if (abs.startsWith(sandbox.root)) {
                    return abs
                }
            } catch (_: Exception) {}
        }

        // Otherwise resolve relative to sandbox root
        val relative = p.trimStart('/')
        val resolved = sandbox.resolve(relative).normalize()
        if (!resolved.startsWith(sandbox.root)) {
            throw SecurityException("Path traversal outside sandbox: $rawPath")
        }
        return resolved
    }

    fun patch(server: OrbitServerImpl, sandbox: SandboxGuard, hideHidden: Boolean) {
        val handlersField = OrbitServerImpl::class.java.getDeclaredField("handlers")
        handlersField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val handlers = handlersField.get(server) as MutableMap<RpcMethod, BiFunction<RPCRequest, StorageEngine, RPCResponse>>

        // PING
        handlers[RpcMethod.PING] = BiFunction { req, _ ->
            RPCResponse(req.requestId(), RPCStatus.PONG, 0, 0, null, null, null, null)
        }

        // OPEN
        handlers[RpcMethod.OPEN] = BiFunction { request, engine ->
            try {
                val raw = request.path()
                val target = resolveSafely(sandbox, raw)
                if (Files.isDirectory(target)) {
                    RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, target.toString(), null, null, null)
                } else {
                    val fd = engine.open(target.toString())
                    RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, fd, null, null, null)
                }
            } catch (e: SecurityException) {
                RPCResponse.error(request.requestId(), 2)
            } catch (e: Exception) {
                RPCResponse.error(request.requestId(), 1)
            }
        }

        // LIST
        handlers[RpcMethod.LIST] = BiFunction { request, _ ->
            try {
                val raw = if (!request.fd().isNullOrEmpty()) request.fd() else request.path()
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
                                val size = if (isDir) 0L else path.fileSize()
                                val lastMod = path.getLastModifiedTime().toMillis()
                                val ext = if (isDir) "" else path.extension
                                val mime = if (isDir) "inode/directory" else (Files.probeContentType(path) ?: "application/octet-stream")
                                RPCResponse.RPCEntry(
                                    path.name,
                                    isDir,
                                    size,
                                    lastMod,
                                    ext,
                                    mime,
                                    "",
                                    ""
                                )
                            } catch (e: Exception) { null }
                        }
                        .toList()
                }
                RPCResponse(
                    request.requestId(),
                    RPCStatus.OK,
                    0,
                    0,
                    null,
                    null,
                    null,
                    entries.sortedWith(compareByDescending<RPCResponse.RPCEntry> { it.isDir() }.thenBy { it.name().lowercase() })
                )
            } catch (e: Exception) {
                RPCResponse.error(request.requestId(), 1)
            }
        }

        // STAT
        handlers[RpcMethod.STAT] = BiFunction { request, engine ->
            try {
                val fd = request.fd()
                if (!fd.isNullOrEmpty()) {
                    try {
                        val stat = engine.stat(fd)
                        val rpcStat = RPCResponse.RPCStat(
                            stat.size(),
                            stat.isDirectory,
                            stat.lastModifiedMillis(),
                            stat.createdMillis(),
                            stat.extension() ?: "",
                            stat.mimeType() ?: "",
                            "",
                            ""
                        )
                        return@BiFunction RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, null, null, rpcStat, null)
                    } catch (_: FileChannelNotFoundException) {
                        // Not an open handle; check direct file path below
                    } catch (_: Exception) {}
                }

                val raw = if (!fd.isNullOrEmpty()) fd else request.path()
                val target = resolveSafely(sandbox, raw)
                if (!Files.exists(target)) {
                    return@BiFunction RPCResponse.error(request.requestId(), 1)
                }
                val isDir = Files.isDirectory(target)
                val size = if (isDir) 0L else Files.size(target)
                val lastMod = Files.getLastModifiedTime(target).toMillis()
                val ext = if (isDir) "" else target.extension
                val mime = if (isDir) "inode/directory" else (Files.probeContentType(target) ?: "application/octet-stream")
                val rpcStat = RPCResponse.RPCStat(
                    size,
                    isDir,
                    lastMod,
                    lastMod,
                    ext,
                    mime,
                    "",
                    ""
                )
                RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, null, null, rpcStat, null)
            } catch (e: Exception) {
                RPCResponse.error(request.requestId(), 1)
            }
        }

        // READ
        handlers[RpcMethod.READ] = BiFunction { request, engine ->
            try {
                val fd = request.fd()
                val offset = request.offset()
                val count = request.count()
                
                val bytes: ByteArray = if (!fd.isNullOrEmpty()) {
                    try {
                        engine.read(fd, offset, count)
                    } catch (_: FileChannelNotFoundException) {
                        readDirectlyFromFile(sandbox, fd, offset, count)
                    }
                } else {
                    readDirectlyFromFile(sandbox, request.path(), offset, count)
                }

                val encoded = if (bytes.isNotEmpty()) Base64.getEncoder().encodeToString(bytes) else null
                RPCResponse(request.requestId(), RPCStatus.OK, 0, bytes.size.toLong(), null, encoded, null, null)
            } catch (e: Exception) {
                RPCResponse.error(request.requestId(), 1)
            }
        }

        // DELETE
        handlers[RpcMethod.DELETE] = BiFunction { request, _ ->
            try {
                val raw = if (!request.path().isNullOrEmpty()) request.path() else request.fd()
                val target = resolveSafely(sandbox, raw)
                if (target == sandbox.root) {
                    return@BiFunction RPCResponse.error(request.requestId(), 1)
                }
                Files.deleteIfExists(target)
                RPCResponse(request.requestId(), RPCStatus.OK, 0, 0, null, null, null, null)
            } catch (e: Exception) {
                RPCResponse.error(request.requestId(), 1)
            }
        }
    }

    private fun readDirectlyFromFile(sandbox: SandboxGuard, rawPath: String?, offset: Long, count: Int): ByteArray {
        val target = resolveSafely(sandbox, rawPath)
        if (!Files.exists(target) || Files.isDirectory(target)) {
            throw StorageException("Cannot read directory or non-existent file: $rawPath")
        }
        return RandomAccessFile(target.toFile(), "r").use { raf ->
            raf.seek(offset)
            val len = minOf(count.toLong(), raf.length() - offset).coerceAtLeast(0L).toInt()
            val buf = ByteArray(len)
            val readBytes = raf.read(buf)
            if (readBytes <= 0) byteArrayOf()
            else if (readBytes < len) buf.copyOf(readBytes)
            else buf
        }
    }
}
