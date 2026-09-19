package org.orbitfs.common.client

import org.orbitfs.common.protocol.*
import org.orbitfs.server.*
import java.nio.file.Path
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiFunction
import timber.log.Timber
import java.util.Base64
import java.util.stream.Collectors
import kotlin.io.path.*

/**
 * The Ultimate Android Compatibility Launcher for OrbitServerImpl.
 * 
 * Bypasses Java 21 Virtual Thread constructors and provides 
 * manual handler overrides to fix bugs in the core library.
 */
object SatelliteServerLauncher {

    fun create(port: Int, rootPath: Path, hideHidden: Boolean): OrbitServerImpl {
        try {
            val normalizedRoot = rootPath.toAbsolutePath().normalize()
            if (!Files.exists(normalizedRoot)) Files.createDirectories(normalizedRoot)
            
            Timber.i("Satellite: Initializing core at $normalizedRoot")
            
            val orbitServerClass = OrbitServerImpl::class.java
            val server = allocateGhostInstance(orbitServerClass)

            // 1. Core Config Injection
            setField(server, "port", port)
            setField(server, "hideHiddenFiles", hideHidden)
            setField(server, "requestCount", AtomicLong(0))
            setField(server, "executor", Executors.newCachedThreadPool())
            
            // 2. Safe Dependencies
            val sandbox = SandboxGuard(normalizedRoot)
            val engine = StorageEngine()
            
            setField(server, "lockRegistry", PathLockRegistry())
            setField(server, "engine", engine)
            setField(server, "sandbox", sandbox)

            // 3. RPC Wiring & Patched Handlers
            val handlers = HashMap<RpcMethod, BiFunction<RPCRequest, StorageEngine, RPCResponse>>()
            setField(server, "handlers", handlers)
            org.orbitfs.common.server.OrbitServerPatcher.patch(server, sandbox, hideHidden)

            Timber.i("Satellite: Core virtualized and patched for Android.")
            return server
        } catch (e: Exception) {
            Timber.e(e, "Satellite: Critical failure")
            throw e
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> allocateGhostInstance(clazz: Class<T>): T {
        return try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val f = unsafeClass.getDeclaredField("theUnsafe")
            f.isAccessible = true
            val unsafe = f.get(null)
            val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
            allocateInstance.invoke(unsafe, clazz) as T
        } catch (e: Exception) {
            // Unsafe is required for Virtual Thread constructor bypass
            throw RuntimeException("Fatal: sun.misc.Unsafe required but unavailable", e)
        }
    }

    private fun setField(obj: Any, fieldName: String, value: Any?) {
        var currentClass: Class<*>? = obj.javaClass
        while (currentClass != null) {
            try {
                val field = currentClass.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(obj, value)
                return
            } catch (e: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }
    }
}
