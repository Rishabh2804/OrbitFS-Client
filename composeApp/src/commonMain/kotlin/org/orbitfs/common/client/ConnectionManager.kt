package org.orbitfs.common.client

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.orbitfs.common.data.ConnectionConfig
import org.orbitfs.common.data.SavedHost
import org.orbitfs.common.util.OrbitLogger

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val config: ConnectionConfig) : ConnectionState()
    data class Connected(val config: ConnectionConfig) : ConnectionState()
    data class Error(val message: String, val config: ConnectionConfig) : ConnectionState()
}

class ConnectionManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val TAG = "ConnectionManager"
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var clientWrapper: OrbitFSClientWrapper? = null
    var activeHostId: String? = null
        private set
    
    var currentConfig = ConnectionConfig()
        private set

    fun connectToSavedHost(host: SavedHost) {
        activeHostId = host.id
        val config = ConnectionConfig(
            name = host.name,
            host = host.host,
            port = host.port,
            guardedDeletion = host.guardedDeletion,
            socketTimeoutMs = host.socketTimeoutMs,
            chunkSizeKb = host.chunkSizeKb,
            autoLoadLimitKb = host.autoLoadLimitKb
        )
        connect(config)
    }

    fun connect(config: ConnectionConfig) {
        currentConfig = config
        scope.launch {
            // FORCE DISCONNECT before new connection to clear stale sessions
            clientWrapper?.let {
                try { it.disconnect() } catch (e: Exception) {}
            }
            clientWrapper = null
            
            _connectionState.value = ConnectionState.Connecting(config)
            try {
                val wrapper = OrbitFSClientWrapper(config)
                wrapper.connect()
                clientWrapper = wrapper
                _connectionState.value = ConnectionState.Connected(config)
                OrbitLogger.i(TAG, "Successfully connected to ${config.host}:${config.port}")
            } catch (e: Exception) {
                OrbitLogger.e(TAG, "Connection failed: ${e.message}")
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error", config)
            }
        }
    }

    fun disconnect() {
        scope.launch {
            clientWrapper?.disconnect()
            clientWrapper = null
            activeHostId = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun getClient(): OrbitFSClientWrapper {
        return clientWrapper ?: throw IllegalStateException("Not connected")
    }
}
