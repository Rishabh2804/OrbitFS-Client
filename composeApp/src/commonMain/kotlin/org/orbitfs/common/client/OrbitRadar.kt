package org.orbitfs.common.client

import kotlinx.coroutines.flow.StateFlow
import org.orbitfs.common.model.OrbiterInfo
import org.orbitfs.common.util.PlatformContext

/**
 * Platform-aware UDP Beacon Radar for instant cross-platform discovery.
 * 
 * Uses expect/actual to handle hardware-specific requirements like 
 * Android MulticastLocks.
 */
expect class OrbitRadar(context: PlatformContext) {
    val discoveredOrbiters: StateFlow<Set<OrbiterInfo>>

    fun registerService(port: Int, pilotName: String, avatarId: String, nodeId: String)
    fun startDiscovery(myNodeId: String)
    fun stopDiscovery()
    fun unregisterService()
}
