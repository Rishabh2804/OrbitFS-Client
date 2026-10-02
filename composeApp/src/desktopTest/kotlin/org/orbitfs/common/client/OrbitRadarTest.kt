package org.orbitfs.common.client

import org.orbitfs.common.util.PlatformContext
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrbitRadarTest {

    @Test
    fun parseBeaconRejectsSelfAndLoopback() {
        val radar = OrbitRadar(PlatformContext())
        val remote = InetAddress.getByName("192.168.1.42")
        val beacon = "ORBITFS_BEACON|Laptop|9090|rocket|node-123"

        val parsed = radar.parseBeacon(beacon, remote, "node-999")
        assertNotNull(parsed)
        assertEquals("OrbitFS-Laptop", parsed.name)
        assertEquals("192.168.1.42", parsed.host)

        val self = radar.parseBeacon(beacon, remote, "node-123")
        assertNull(self)

        val loopback = radar.parseBeacon(beacon, InetAddress.getLoopbackAddress(), "node-999")
        assertNull(loopback)
    }

    @Test
    fun buildBroadcastTargetsIncludesGlobalFallback() {
        val radar = OrbitRadar(PlatformContext())
        val targets = radar.collectBroadcastTargets()
        assertTrue(targets.isNotEmpty())
        assertTrue(targets.any { it.hostAddress == "255.255.255.255" })
    }
}
