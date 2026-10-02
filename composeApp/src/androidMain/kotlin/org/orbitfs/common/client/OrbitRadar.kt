package org.orbitfs.common.client

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.orbitfs.common.model.OrbiterInfo
import org.orbitfs.common.util.PlatformContext
import org.orbitfs.common.util.OrbitLogger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * Android implementation of OrbitRadar with MulticastLock support for physical hardware discovery.
 */
actual class OrbitRadar actual constructor(context: PlatformContext) {
    private val TAG = "OrbitRadar"
    private val androidContext = context.context
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _discoveredOrbiters = MutableStateFlow<Set<OrbiterInfo>>(emptySet())
    actual val discoveredOrbiters: StateFlow<Set<OrbiterInfo>> = _discoveredOrbiters.asStateFlow()

    private var broadcastJob: Job? = null
    private var listenerJob: Job? = null
    private var prunerJob: Job? = null
    private var localNodeId: String? = null
    private val lastSeenMap = ConcurrentHashMap<String, Long>()

    private var multicastLock: WifiManager.MulticastLock? = null

    private fun acquireLock() {
        if (multicastLock == null) {
            val wifi = androidContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("OrbitFSRadarLock").apply {
                setReferenceCounted(true)
                acquire()
            }
            OrbitLogger.d(TAG, "MulticastLock acquired")
        }
    }

    private fun releaseLock() {
        multicastLock?.let {
            if (it.isHeld) it.release()
            multicastLock = null
            OrbitLogger.d(TAG, "MulticastLock released")
        }
    }

    actual fun registerService(port: Int, pilotName: String, avatarId: String, nodeId: String) {
        localNodeId = nodeId
        startBroadcasting(port, pilotName, avatarId, nodeId)
    }

    private fun startBroadcasting(port: Int, pilotName: String, avatarId: String, nodeId: String) {
        broadcastJob?.cancel()
        broadcastJob = scope.launch {
            val socket = try {
                DatagramSocket().apply { broadcast = true }
            } catch (e: Exception) {
                return@launch
            }
            
            val message = "ORBITFS_BEACON|$pilotName|$port|$avatarId|$nodeId".toByteArray()
            
            while (isActive) {
                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    while (interfaces.hasMoreElements()) {
                        val ni = interfaces.nextElement()
                        if (ni.isLoopback || !ni.isUp) continue
                        ni.interfaceAddresses.forEach { addr ->
                            val broadcastAddr = addr.broadcast ?: return@forEach
                            socket.send(DatagramPacket(message, message.size, broadcastAddr, DISCOVERY_PORT))
                        }
                    }
                    socket.send(DatagramPacket(message, message.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT))
                } catch (e: Exception) {}
                delay(1000)
            }
            socket.close()
        }
    }

    actual fun startDiscovery(myNodeId: String) {
        if (listenerJob != null) return
        localNodeId = myNodeId
        acquireLock()
        
        prunerJob?.cancel()
        prunerJob = scope.launch {
            while (isActive) {
                delay(2000)
                val now = System.currentTimeMillis()
                val stale = lastSeenMap.filterValues { now - it > 5000 }.keys
                if (stale.isNotEmpty()) {
                    _discoveredOrbiters.update { current -> current.filter { it.nodeId !in stale }.toSet() }
                    stale.forEach { lastSeenMap.remove(it) }
                }
            }
        }

        listenerJob = scope.launch {
            val socket = try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                    soTimeout = 3000
                }
            } catch (e: Exception) {
                return@launch
            }
            
            val buffer = ByteArray(2048)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val data = String(packet.data, 0, packet.length)
                    if (data.startsWith("ORBITFS_BEACON")) {
                        val parts = data.split("|")
                        if (parts.size >= 5) {
                            val name = parts[1]
                            val nodeId = parts[4]
                            if (nodeId == localNodeId) continue
                            val host = packet.address.hostAddress
                            val port = parts[2].toInt()
                            lastSeenMap[nodeId] = System.currentTimeMillis()
                            _discoveredOrbiters.update { current ->
                                current.filter { it.nodeId != nodeId && !(it.host == host && it.port == port) }.toSet() + OrbiterInfo(
                                    nodeId = nodeId, name = name, host = host,
                                    port = port, avatarId = parts[3]
                                )
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
            socket.close()
        }
    }

    actual fun stopDiscovery() {
        listenerJob?.cancel()
        listenerJob = null
        prunerJob?.cancel()
        prunerJob = null
        releaseLock()
        _discoveredOrbiters.value = emptySet()
        lastSeenMap.clear()
    }

    actual fun unregisterService() {
        broadcastJob?.cancel()
        broadcastJob = null
    }

    companion object {
        private const val DISCOVERY_PORT = 9999
    }
}
