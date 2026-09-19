package org.orbitfs.common.data

import kotlinx.coroutines.flow.StateFlow
import org.orbitfs.common.util.PlatformContext

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

expect class HostRepository(context: PlatformContext) {
    val hosts: StateFlow<List<SavedHost>>
    fun addHost(host: SavedHost)
    fun removeHost(id: String)
    fun updateHost(host: SavedHost)
}
