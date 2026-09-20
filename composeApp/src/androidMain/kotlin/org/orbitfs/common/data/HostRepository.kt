package org.orbitfs.common.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.orbitfs.common.util.PlatformContext
import java.io.File
import java.util.UUID

actual class HostRepository actual constructor(context: PlatformContext) {
    private val hostsFile = File(context.context.filesDir, "hosts_v2.json")
    private val _hosts = MutableStateFlow<List<SavedHost>>(emptyList())
    actual val hosts: StateFlow<List<SavedHost>> = _hosts.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        loadHosts()
    }

    private fun loadHosts() {
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

    private fun saveHosts() {
        try {
            val content = json.encodeToString(_hosts.value)
            hostsFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun addHost(host: SavedHost) {
        val current = _hosts.value.toMutableList()
        val id = if (host.id.isBlank()) UUID.randomUUID().toString() else host.id
        current.add(0, host.copy(id = id))
        _hosts.value = current
        saveHosts()
    }

    actual fun removeHost(id: String) {
        val current = _hosts.value.toMutableList()
        current.removeAll { it.id == id }
        _hosts.value = current
        saveHosts()
    }

    actual fun updateHost(host: SavedHost) {
        val current = _hosts.value.map {
            if (it.id == host.id) host else it
        }
        _hosts.value = current
        saveHosts()
    }
}
