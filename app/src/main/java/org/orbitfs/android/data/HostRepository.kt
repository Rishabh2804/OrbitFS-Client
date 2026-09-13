package org.orbitfs.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedHost(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val authToken: String? = null,
    val guardedDeletion: Boolean = true,
    val socketTimeoutMs: Int = 10_000,
    val chunkSizeKb: Int = 256,
    val autoLoadLimitKb: Int = 512
)

data class ConnectionConfig(
    val name: String = "Satellite",
    val host: String = "192.168.0.5",
    val port: Int = 9090,
    val authToken: String? = null,
    val guardedDeletion: Boolean = true,
    val socketTimeoutMs: Int = 10_000,
    val chunkSizeKb: Int = 256,
    val autoLoadLimitKb: Int = 512
) {
    fun toSavedHost(name: String) = SavedHost(
        id = name,
        name = name,
        host = host,
        port = port,
        authToken = authToken,
        guardedDeletion = guardedDeletion,
        socketTimeoutMs = socketTimeoutMs,
        chunkSizeKb = chunkSizeKb,
        autoLoadLimitKb = autoLoadLimitKb
    )
}

class HostRepository(
    private val hostsFile: File
) {
    private val _hosts = MutableStateFlow<List<SavedHost>>(emptyList())
    val hosts: StateFlow<List<SavedHost>> = _hosts.asStateFlow()

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

    fun addHost(host: SavedHost) {
        val current = _hosts.value.toMutableList()
        current.add(0, host.copy(id = System.currentTimeMillis().toString()))
        _hosts.value = current
        saveHosts()
    }

    fun removeHost(id: String) {
        val current = _hosts.value.toMutableList()
        current.removeAll { it.id == id }
        _hosts.value = current
        saveHosts()
    }

    fun updateHost(host: SavedHost) {
        val current = _hosts.value.map {
            if (it.id == host.id) host else it
        }
        _hosts.value = current
        saveHosts()
    }
}