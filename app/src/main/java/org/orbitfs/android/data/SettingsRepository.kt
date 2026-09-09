package org.orbitfs.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ConnectionConfig(
    val host: String = "192.168.0.5",
    val port: Int = 9090,
    val authToken: String? = null
)

class SettingsRepository(
    private var config: ConnectionConfig = ConnectionConfig()
) {

    private val _config = MutableStateFlow(config)
    val configFlow: StateFlow<ConnectionConfig> = _config

    val host: String get() = config.host
    val port: Int get() = config.port
    val currentConfig: ConnectionConfig get() = config

    fun updateConfig(newConfig: ConnectionConfig) {
        config = newConfig
        _config.update { newConfig }
    }
}
