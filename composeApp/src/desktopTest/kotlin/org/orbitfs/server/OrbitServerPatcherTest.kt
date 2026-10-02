package org.orbitfs.server

import org.orbitfs.common.protocol.*
import org.orbitfs.common.server.OrbitServerPatcher
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlin.test.*

class OrbitServerPatcherTest {

    @Test
    fun testResolveSafely() {
        val tempDir = Files.createTempDirectory("orbit_test_sandbox")
        try {
            val sandbox = SandboxGuard(tempDir)

            // Test root notations
            assertEquals(tempDir, OrbitServerPatcher.resolveSafely(sandbox, ""))
            assertEquals(tempDir, OrbitServerPatcher.resolveSafely(sandbox, "/"))
            assertEquals(tempDir, OrbitServerPatcher.resolveSafely(sandbox, "."))
            assertEquals(tempDir, OrbitServerPatcher.resolveSafely(sandbox, null))

            // Test relative and leading slash paths
            val expectedDocs = tempDir.resolve("docs").normalize()
            assertEquals(expectedDocs, OrbitServerPatcher.resolveSafely(sandbox, "docs"))
            assertEquals(expectedDocs, OrbitServerPatcher.resolveSafely(sandbox, "/docs"))
            assertEquals(expectedDocs, OrbitServerPatcher.resolveSafely(sandbox, "docs/"))
            assertEquals(expectedDocs, OrbitServerPatcher.resolveSafely(sandbox, "/docs/"))

            // Nested
            val expectedNested = tempDir.resolve("docs/file.txt").normalize()
            assertEquals(expectedNested, OrbitServerPatcher.resolveSafely(sandbox, "docs/file.txt"))
            assertEquals(expectedNested, OrbitServerPatcher.resolveSafely(sandbox, "/docs/file.txt"))

            // Absolute path starting with sandbox root
            assertEquals(expectedNested, OrbitServerPatcher.resolveSafely(sandbox, expectedNested.toString()))

            // Traversal attempt should remain bounded to sandbox or throw
            val traversal = OrbitServerPatcher.resolveSafely(sandbox, "../../secret.txt")
            assertTrue(traversal.startsWith(tempDir), "Resolved path must not escape sandbox: $traversal")
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun testRpcHandlersFixed() {
        val tempDir = Files.createTempDirectory("orbit_rpc_test")
        try {
            val docsDir = File(tempDir.toFile(), "docs").apply { mkdirs() }
            val fileA = File(docsDir, "test.txt").apply { writeText("Hello OrbitFS!") }
            val fileRoot = File(tempDir.toFile(), "root_file.txt").apply { writeText("Root content") }

            val server = OrbitServerImpl(0, tempDir, false)
            val sandboxField = OrbitServerImpl::class.java.getDeclaredField("sandbox")
            sandboxField.isAccessible = true
            val sandbox = sandboxField.get(server) as SandboxGuard
            OrbitServerPatcher.patch(server, sandbox, false)

            val handlersField = OrbitServerImpl::class.java.getDeclaredField("handlers")
            handlersField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val handlers = handlersField.get(server) as Map<RpcMethod, java.util.function.BiFunction<RPCRequest, StorageEngine, RPCResponse>>
            val engine = StorageEngine()

            // 1. LIST root (fd = null or "")
            val listRootReq = RPCRequest("req1", RpcMethod.LIST, null, "", 0L, 0, null, true)
            val rootResp = handlers[RpcMethod.LIST]!!.apply(listRootReq, engine)
            assertEquals(RPCStatus.OK, rootResp.status())
            val rootNames = rootResp.listing().map { it.name() }
            assertTrue(rootNames.contains("docs"), "Root must contain 'docs'")
            assertTrue(rootNames.contains("root_file.txt"), "Root must contain 'root_file.txt'")

            // 2. LIST subfolder (fd = "docs") - VERIFY RECURSIVE PATH BUG IS FIXED!
            val listDocsReq = RPCRequest("req2", RpcMethod.LIST, null, "docs", 0L, 0, null, true)
            val docsResp = handlers[RpcMethod.LIST]!!.apply(listDocsReq, engine)
            assertEquals(RPCStatus.OK, docsResp.status())
            val docsNames = docsResp.listing().map { it.name() }
            assertTrue(docsNames.contains("test.txt"), "docs must contain 'test.txt'")
            assertFalse(docsNames.contains("docs"), "docs subfolder must NOT contain 'docs' (no recursion!)")

            // 3. STAT direct path (fd = "/docs/test.txt")
            val statReq = RPCRequest("req3", RpcMethod.STAT, null, "/docs/test.txt", 0L, 0, null, null)
            val statResp = handlers[RpcMethod.STAT]!!.apply(statReq, engine)
            assertEquals(RPCStatus.OK, statResp.status())
            assertNotNull(statResp.stat(), "Stat response must not be null")
            assertEquals(14L, statResp.stat().size(), "File size must match 'Hello OrbitFS!'")
            assertFalse(statResp.stat().isDir())

            // 4. READ direct path (fd = "docs/test.txt")
            val readReq = RPCRequest("req4", RpcMethod.READ, null, "docs/test.txt", 0L, 14, null, null)
            val readResp = handlers[RpcMethod.READ]!!.apply(readReq, engine)
            assertEquals(RPCStatus.OK, readResp.status())
            val content = String(Base64.getDecoder().decode(readResp.dataBase64()))
            assertEquals("Hello OrbitFS!", content)

            // 5. DELETE direct path
            val delReq = RPCRequest("req5", RpcMethod.DELETE, "docs/test.txt", null, 0L, 0, null, null)
            val delResp = handlers[RpcMethod.DELETE]!!.apply(delReq, engine)
            assertEquals(RPCStatus.OK, delResp.status())
            assertFalse(fileA.exists(), "test.txt should be deleted")
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
