package org.orbitfs.android.model

data class SatelliteState(
    val isRunning: Boolean = false,
    val port: Int = 9090,
    val rootPath: String? = null,
    val rootUri: String? = null,
    val pairingToken: String? = null,
    val connectedClients: Int = 0,
    val totalBytesServed: Long = 0L,
    val error: String? = null
)
