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
    val authToken: String? = null
)

data class ConnectionConfig(
    val host: String = "192.168.0.5",
    val port: Int = 9090,
    val authToken: String? = null
) {
    fun toSavedHost(name: String) = SavedHost(
        id = name,
        name = name,
        host = host,
        port = port,
        authToken = authToken
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
                        authToken = obj.optString("authToken").takeIf { it.isNotEmpty() }
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