package org.orbitfs.android.client

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.orbitfs.android.data.ConnectionConfig
import org.orbitfs.android.data.SavedHost
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val TAG = "ConnectionManager"

class ConnectionManager(
    private val scope: CoroutineScope
) {

    private var client: OrbitFSClientWrapper? = null
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected(ConnectionConfig()))
    val connectionState: StateFlow<ConnectionState> = _connectionState

    val currentConfig: ConnectionConfig
        get() = connectionState.value.config

    fun connect(config: ConnectionConfig) {
        val oldClient = client
        client = null

        _connectionState.update { ConnectionState.Connecting(config) }

        if (oldClient != null) {
            scope.launch { oldClient.disconnect() }
        }

        scope.launch {
            var attempts = 0
            val maxAttempts = 5
            var delayMs: Duration = 1.seconds

            while (isActive && attempts < maxAttempts) {
                attempts++
                try {
                    val wrapper = OrbitFSClientWrapper(config.host, config.port)
                    val result = wrapper.connect()

                    result.onSuccess {
                        client = wrapper
                        _connectionState.update { ConnectionState.Connected(config) }
                        Log.d(TAG, "Connected on attempt $attempts to ${config.host}:${config.port}")
                        startReconnectMonitor()
                    }.onFailure { error ->
                        _connectionState.update {
                            ConnectionState.Error(
                                config = config,
                                message = error.message ?: "Connection failed",
                                attempt = attempts,
                                maxAttempts = maxAttempts
                            )
                        }
                        Log.e(TAG, "Connect attempt $attempts failed", error)
                    }

                    if (_connectionState.value is ConnectionState.Connected) break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _connectionState.update {
                        ConnectionState.Error(
                            config = config,
                            message = e.message ?: "Connection failed",
                            attempt = attempts,
                            maxAttempts = maxAttempts
                        )
                    }
                    Log.e(TAG, "Connect attempt $attempts failed", e)
                }

                if (isActive && _connectionState.value !is ConnectionState.Connected) {
                    delay(delayMs)
                    delayMs = (delayMs * 2.0).coerceAtMost(60.seconds)
                }
            }

            if (isActive && _connectionState.value !is ConnectionState.Connected && attempts >= maxAttempts) {
                _connectionState.update {
                    ConnectionState.GaveUp(config, "Could not connect after $maxAttempts attempts")
                }
            }
        }
    }

    private fun startReconnectMonitor() {
        reconnectJob = scope.launch {
            while (isActive) {
                val current = _connectionState.value
                if (current is ConnectionState.Connected) {
                    val wrapper = client ?: break
                    try {
                        wrapper.ensureConnected()
                    } catch (e: Exception) {
                        Log.w(TAG, "Connection lost, will reconnect", e)
                        _connectionState.update { ConnectionState.Disconnected(current.config) }
                    }
                }
                delay(5000.milliseconds)
            }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        scope.launch {
            client?.disconnect()
        }
        client = null
        _connectionState.update { ConnectionState.Disconnected(currentConfig) }
    }

    suspend fun getClient(): OrbitFSClientWrapper {
        val c = client
        if (c == null || !c.isConnected) {
            throw IOException("Client not connected")
        }
        return c
    }

    fun isConnected(): Boolean {
        return connectionState.value is ConnectionState.Connected
    }

    fun connectToSavedHost(host: SavedHost) {
        connect(ConnectionConfig(host.host, host.port, host.authToken))
    }
}

sealed class ConnectionState {
    abstract val config: ConnectionConfig

    data class Disconnected(override val config: ConnectionConfig) : ConnectionState()
    data class Connecting(override val config: ConnectionConfig) : ConnectionState()
    data class Connected(override val config: ConnectionConfig) : ConnectionState()
    data class Error(
        override val config: ConnectionConfig,
        val message: String,
        val attempt: Int,
        val maxAttempts: Int
    ) : ConnectionState()

    data class GaveUp(
        override val config: ConnectionConfig,
        val message: String
    ) : ConnectionState()
}
