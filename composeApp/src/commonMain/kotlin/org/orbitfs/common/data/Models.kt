package org.orbitfs.common.data

data class ConnectionConfig(
    val name: String = "Satellite",
    val host: String = "192.168.0.5",
    val port: Int = 9090,
    val authToken: String? = null,
    val guardedDeletion: Boolean = true,
    val socketTimeoutMs: Int = 10_000,
    val chunkSizeKb: Int = 256,
    val autoLoadLimitKb: Int = 512
)
