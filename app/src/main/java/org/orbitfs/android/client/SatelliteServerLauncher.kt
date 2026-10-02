package org.orbitfs.android.client

import org.orbitfs.common.protocol.RpcMethod
import org.orbitfs.server.OrbitServerImpl
import org.orbitfs.server.StorageEngine
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber
import java.util.function.BiFunction

/**
 * A workaround launcher for OrbitServerImpl on Android.
 * Bypasses constructor to avoid Java 21 Virtual Thread dependency.
 */
object SatelliteServerLauncher {

    fun create(port: Int, rootPath: Path, hideHidden: Boolean): OrbitServerImpl {
        try {
            val orbitServerClass = OrbitServerImpl::class.java
            val server = instantiate(orbitServerClass)

            // 1. Initialize OrbitServerImpl fields
            setField(server, "port", port)
            setField(server, "hideHiddenFiles", hideHidden)
            
            // StorageEngine and its FileDescriptorTable
            val engineClass = StorageEngine::class.java
            val engine = instantiate(engineClass)
            
            val fdTableClass = Class.forName("org.orbitfs.server.FileDescriptorTable")
            val fdTable = instantiate(fdTableClass)
            setField(fdTable, "handles", ConcurrentHashMap<Any, Any>())
            
            setField(engine, "table", fdTable)
            setField(server, "engine", engine)
            
            // PathLockRegistry
            val lockRegistryClass = Class.forName("org.orbitfs.server.PathLockRegistry")
            val lockRegistry = instantiate(lockRegistryClass)
            setField(lockRegistry, "locks", ConcurrentHashMap<Any, Any>())
            setField(server, "lockRegistry", lockRegistry)

            // SandboxGuard
            val sandboxGuardClass = Class.forName("org.orbitfs.server.SandboxGuard")
            val sandboxGuard = instantiate(sandboxGuardClass)
            setField(sandboxGuard, "root", rootPath)
            setField(server, "sandbox", sandboxGuard)

            // Executor - Use Standard Thread Pool
            setField(server, "executor", Executors.newCachedThreadPool())
            setField(server, "requestCount", AtomicLong(0))
            
            val handlers = HashMap<RpcMethod, BiFunction<*, *, *>>()
            setField(server, "handlers", handlers)

            // 2. Register Handlers
            val registerMethod = orbitServerClass.getDeclaredMethod("registerHandlers")
            registerMethod.isAccessible = true
            registerMethod.invoke(server)

            Timber.i("OrbitServerImpl fully initialized via compatibility layer (Deep Scan). Handlers: ${handlers.size}")
            return server
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch satellite server")
            throw e
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> instantiate(clazz: Class<T>): T {
        return try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val f = unsafeClass.getDeclaredField("theUnsafe")
            f.isAccessible = true
            val unsafe = f.get(null)
            val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
            allocateInstance.invoke(unsafe, clazz) as T
        } catch (e: Exception) {
            val constructor = clazz.getDeclaredConstructor()
            constructor.isAccessible = true
            constructor.newInstance() as T
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
        throw NoSuchFieldException("Field $fieldName not found in ${obj.javaClass}")
    }
}
