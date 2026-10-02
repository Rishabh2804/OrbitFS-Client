package org.orbitfs.common.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.orbitfs.common.util.PlatformContext
import java.io.File
import java.util.*

actual class HostRepository actual constructor(context: PlatformContext) {
    private val hostsFile = File(System.getProperty("user.home"), ".orbitfs_hosts_v2.json")
    private val _hosts = MutableStateFlow<List<SavedHost>>(emptyList())
    actual val hosts: StateFlow<List<SavedHost>> = _hosts

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init { load() }

    private fun load() {
        try {
            if (hostsFile.exists()) {
                val content = hostsFile.readText()
                val list = json.decodeFromString<List<SavedHost>>(content)
                _hosts.value = list
            }
        } catch (e: Exception) {
            _hosts.value = emptyList()
        }
    }

    private fun save() {
        try {
            val content = json.encodeToString(_hosts.value)
            hostsFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun addHost(host: SavedHost) {
        val id = if (host.id.isBlank()) UUID.randomUUID().toString() else host.id
        _hosts.value = _hosts.value + host.copy(id = id)
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
