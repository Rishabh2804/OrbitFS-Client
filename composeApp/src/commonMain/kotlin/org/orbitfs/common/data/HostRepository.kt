package org.orbitfs.common.data

import kotlinx.coroutines.flow.StateFlow
import org.orbitfs.common.util.PlatformContext

expect class HostRepository(context: PlatformContext) {
    val hosts: StateFlow<List<SavedHost>>
    fun addHost(host: SavedHost)
    fun removeHost(id: String)
    fun updateHost(host: SavedHost)
}
