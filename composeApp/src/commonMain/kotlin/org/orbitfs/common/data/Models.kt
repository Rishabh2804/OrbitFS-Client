package org.orbitfs.common.data

import kotlinx.serialization.Serializable

@Serializable
data class SavedHost(
    val id: String, // UUID for internal tracking
    val nodeId: String = "", // Persistent identifier from remote device
    val name: String,
    val host: String,
    val port: Int,
    val authToken: String? = null,
    val guardedDeletion: Boolean = true,
    val socketTimeoutMs: Int = 10000,
    val chunkSizeKb: Int = 64,
    val autoLoadLimitKb: Int = 512,
    val lastSeenAvatarId: String? = null
)

@Serializable
data class ConnectionConfig(
    val name: String = "",
    val host: String = "localhost",
    val port: Int = 9090,
    val guardedDeletion: Boolean = true,
    val socketTimeoutMs: Int = 10000,
    val chunkSizeKb: Int = 64,
    val autoLoadLimitKb: Int = 512
)
