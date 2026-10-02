package org.orbitfs.common.model

data class OrbiterInfo(
    val nodeId: String,
    val name: String,
    val host: String,
    val port: Int,
    val avatarId: String? = null
)
