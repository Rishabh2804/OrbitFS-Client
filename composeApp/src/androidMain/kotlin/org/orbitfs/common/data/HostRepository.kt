package org.orbitfs.common.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.orbitfs.common.util.PlatformContext
import java.io.File

actual class HostRepository actual constructor(context: PlatformContext) {
    private val hostsFile = File(context.context.filesDir, "hosts.json")
    private val _hosts = MutableStateFlow<List<SavedHost>>(emptyList())
    actual val hosts: StateFlow<List<SavedHost>> = _hosts.asStateFlow()

    init {
        loadHosts()
    }

    private fun loadHosts() {
        try {
            if (hostsFile.exists()) {
                val json = hostsFile.readText()
                val arr = JSONArray(json)
                val list = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    SavedHost(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        host = obj.getString("host"),
                        port = obj.getInt("port"),
                        authToken = obj.optString("authToken").takeIf { it.isNotEmpty() },
                        guardedDeletion = obj.optBoolean("guardedDeletion", true),
                        socketTimeoutMs = obj.optInt("socketTimeoutMs", 10_000),
                        chunkSizeKb = obj.optInt("chunkSizeKb", 256),
                        autoLoadLimitKb = obj.optInt("autoLoadLimitKb", 512)
                    )
                }
                _hosts.value = list
            }
        } catch (e: Exception) {
            _hosts.value = emptyList()
        }
    }

    private fun saveHosts() {
        try {
            val arr = JSONArray()
            for (h in _hosts.value) {
                arr.put(JSONObject().apply {
                    put("id", h.id)
                    put("name", h.name)
                    put("host", h.host)
                    put("port", h.port)
                    put("authToken", h.authToken ?: "")
                    put("guardedDeletion", h.guardedDeletion)
                    put("socketTimeoutMs", h.socketTimeoutMs)
                    put("chunkSizeKb", h.chunkSizeKb)
                    put("autoLoadLimitKb", h.autoLoadLimitKb)
                })
            }
            hostsFile.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun addHost(host: SavedHost) {
        val current = _hosts.value.toMutableList()
        current.add(0, host.copy(id = System.currentTimeMillis().toString()))
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
