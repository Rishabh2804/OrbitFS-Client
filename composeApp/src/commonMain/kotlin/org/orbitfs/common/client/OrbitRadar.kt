package org.orbitfs.common.client

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.orbitfs.common.model.OrbiterInfo
import org.orbitfs.common.util.PlatformContext
import org.orbitfs.common.util.OrbitLogger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Robust UDP Beacon Radar for instant cross-platform discovery.
 */
class OrbitRadar(context: PlatformContext) {
    private val TAG = "OrbitRadar"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _discoveredOrbiters = MutableStateFlow<Set<OrbiterInfo>>(emptySet())
    val discoveredOrbiters: StateFlow<Set<OrbiterInfo>> = _discoveredOrbiters.asStateFlow()

    private var broadcastJob: Job? = null
    private var listenerJob: Job? = null
    private var prunerJob: Job? = null
    private var localNodeId: String? = null
    private val localIPs = mutableSetOf<String>()
    private val lastSeenMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

    init {
        scope.launch {
            refreshLocalIPs()
        }
    }

    private fun refreshLocalIPs() {
        try {
            localIPs.clear()
            localIPs.add("127.0.0.1")
            localIPs.add("0.0.0.0")
            localIPs.add("localhost")
            localIPs.add("::1")
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    addr.hostAddress?.let { hostAddress ->
                        val clean = hostAddress.substringBefore("%")
                        localIPs.add(clean)
                    }
                }
            }
        } catch (e: Exception) {
            OrbitLogger.e(TAG, "Unable to refresh local IP addresses", e)
        }
    }

    fun registerService(port: Int, pilotName: String, avatarId: String, nodeId: String) {
        localNodeId = nodeId
        refreshLocalIPs()
        startBroadcasting(port, pilotName, avatarId, nodeId)
    }

    internal fun collectBroadcastTargets(): Set<InetAddress> {
        val targets = linkedSetOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptySet()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp) continue
                ni.interfaceAddresses.forEach { addr ->
                    val broadcast = addr.broadcast
                    if (broadcast is Inet4Address) {
                        targets += broadcast
                    }
                }
            }
            targets += InetAddress.getByName("255.255.255.255")
        } catch (e: Exception) {
            OrbitLogger.e(TAG, "Unable to enumerate broadcast targets", e)
        }
        return targets.filterIsInstance<Inet4Address>().toSet()
    }

    internal fun parseBeacon(data: String, packetAddress: InetAddress, localNodeId: String?): OrbiterInfo? {
        if (!data.startsWith("ORBITFS_BEACON")) return null
        val parts = data.split("|")
        if (parts.size < 5) return null
        val advertisedName = parts[1].trim()
        val name = if (advertisedName.isNotEmpty()) "OrbitFS-$advertisedName" else "OrbitFS-Unknown"
        val port = parts[2].trim().toIntOrNull() ?: return null
        val avatarId = parts[3].trim()
        val nodeId = parts[4].trim()
        if (nodeId.isNotEmpty() && localNodeId != null && nodeId == localNodeId) return null
        val host = packetAddress.hostAddress?.substringBefore("%") ?: return null
        if (packetAddress.isLoopbackAddress || packetAddress.isAnyLocalAddress || localIPs.contains(host)) return null
        return OrbiterInfo(
            name = name,
            host = host,
            port = port,
            avatarId = avatarId.ifEmpty { null }
        )
    }

    private fun startBroadcasting(port: Int, pilotName: String, avatarId: String, nodeId: String) {
        broadcastJob?.cancel()
        broadcastJob = scope.launch {
            println("OrbitRadar: Starting UDP Broadcast Beacon on port $DISCOVERY_PORT")
            val socket = try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                }
            } catch (e: Exception) {
                println("OrbitRadar: Failed to create broadcast socket: ${e.message}")
                return@launch
            }

            val message = "ORBITFS_BEACON|$pilotName|$port|$avatarId|$nodeId".toByteArray()
            while (isActive) {
                try {
                    val targets = collectBroadcastTargets()
                    if (targets.isEmpty()) {
                        val fallback = DatagramPacket(message, message.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
                        socket.send(fallback)
                    } else {
                        targets.forEach { target ->
                            val packet = DatagramPacket(message, message.size, target, DISCOVERY_PORT)
                            socket.send(packet)
                        }
                    }
                } catch (e: Exception) {
                    println("OrbitRadar: Broadcast error: ${e.message}")
                }
                delay(2000)
            }
            socket.close()
        }
    }

    fun startDiscovery(myNodeId: String) {
        if (listenerJob != null) return
        localNodeId = myNodeId
        refreshLocalIPs()
        
        prunerJob?.cancel()
        prunerJob = scope.launch {
            while (isActive) {
                delay(3000)
                val now = System.currentTimeMillis()
                val stale = lastSeenMap.entries.filter { now - it.value > 6000L }.map { it.key }.toSet()
                if (stale.isNotEmpty()) {
                    stale.forEach { lastSeenMap.remove(it) }
                    _discoveredOrbiters.update { current ->
                        current.filter { it.name !in stale }.toSet()
                    }
                }
            }
        }

        listenerJob = scope.launch {
            println("OrbitRadar: Starting Discovery Listener on port $DISCOVERY_PORT")
            val socket = try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(DISCOVERY_PORT))
                    soTimeout = 3000
                }
            } catch (e: Exception) {
                println("OrbitRadar: Discovery bind failed on port $DISCOVERY_PORT: ${e.message}")
                return@launch
            }
            
            val buffer = ByteArray(2048)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val data = String(packet.data, 0, packet.length)
                    val discovered = parseBeacon(data, packet.address, localNodeId)
                    if (discovered == null) continue

                    lastSeenMap[discovered.name] = System.currentTimeMillis()
                    _discoveredOrbiters.update { current ->
                        current.filter { it.name != discovered.name }.toSet() + discovered
                    }
                } catch (e: Exception) {}
            }
            socket.close()
        }
    }

    fun stopDiscovery() {
        listenerJob?.cancel()
        listenerJob = null
        prunerJob?.cancel()
        prunerJob = null
        lastSeenMap.clear()
        _discoveredOrbiters.value = emptySet()
    }

    fun unregisterService() {
        broadcastJob?.cancel()
        broadcastJob = null
        localNodeId = null
    }

    companion object {
        private const val DISCOVERY_PORT = 9999
    }
}
