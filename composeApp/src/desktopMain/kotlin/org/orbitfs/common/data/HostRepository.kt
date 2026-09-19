package org.orbitfs.common.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.orbitfs.common.util.PlatformContext
import java.io.File
import java.util.*

actual class HostRepository actual constructor(context: PlatformContext) {
    private val hostsFile = File(System.getProperty("user.home"), ".orbitfs_hosts.properties")
    private val _hosts = MutableStateFlow<List<SavedHost>>(emptyList())
    actual val hosts: StateFlow<List<SavedHost>> = _hosts

    init { load() }

    private fun load() {
        if (!hostsFile.exists()) return
        val props = Properties()
        hostsFile.inputStream().use { props.load(it) }

        val list = props.stringPropertyNames().mapNotNull { id ->
            val value = props.getProperty(id) ?: return@mapNotNull null
            val parts = value.split("|")
            if (parts.size < 3) return@mapNotNull null

            SavedHost(
                id = id,
                name = parts[0],
                host = parts[1],
                port = parts[2].toIntOrNull() ?: 9090,
                authToken = parts.getOrNull(3)?.takeIf { it.isNotEmpty() },
                guardedDeletion = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: true,
                socketTimeoutMs = parts.getOrNull(5)?.toIntOrNull() ?: 10_000,
                chunkSizeKb = parts.getOrNull(6)?.toIntOrNull() ?: 256,
                autoLoadLimitKb = parts.getOrNull(7)?.toIntOrNull() ?: 512
            )
        }
        _hosts.value = list
    }

    private fun save() {
        val props = Properties()
        _hosts.value.forEach { host ->
            val serialized = listOf(
                host.name,
                host.host,
                host.port.toString(),
                host.authToken.orEmpty(),
                host.guardedDeletion.toString(),
                host.socketTimeoutMs.toString(),
                host.chunkSizeKb.toString(),
                host.autoLoadLimitKb.toString()
            ).joinToString("|")
            props.setProperty(host.id, serialized)
        }
        hostsFile.outputStream().use { props.store(it, "OrbitFS Saved Hosts") }
    }

    actual fun addHost(host: SavedHost) {
        val newHost = if (host.id.isEmpty()) host.copy(id = UUID.randomUUID().toString()) else host
        _hosts.value = _hosts.value + newHost
        save()
    }

    actual fun removeHost(id: String) {
        _hosts.value = _hosts.value.filter { it.id != id }
        save()
    }

    actual fun updateHost(host: SavedHost) {
        _hosts.value = _hosts.value.map { if (it.id == host.id) host else it }
        save()
    }
}
