package org.orbitfs.common.data

import org.orbitfs.common.util.PlatformContext
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistenceRegressionTest {

    @Test
    fun `desktop host repository preserves all saved host settings`() {
        val originalHome = System.getProperty("user.home")
        val tempHome = Files.createTempDirectory("orbitfs-hosts-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)

        try {
            val repo = HostRepository(PlatformContext())
            val host = SavedHost(
                id = "host-1",
                name = "Laptop",
                host = "10.0.0.42",
                port = 9090,
                authToken = "abc123",
                guardedDeletion = false,
                socketTimeoutMs = 5000,
                chunkSizeKb = 128,
                autoLoadLimitKb = 256
            )

            repo.addHost(host)
            val reloaded = HostRepository(PlatformContext())

            assertEquals(1, reloaded.hosts.value.size)
            val restored = reloaded.hosts.value.first()
            assertEquals(host.name, restored.name)
            assertEquals(host.host, restored.host)
            assertEquals(host.port, restored.port)
            assertEquals(host.authToken, restored.authToken)
            assertEquals(host.guardedDeletion, restored.guardedDeletion)
            assertEquals(host.socketTimeoutMs, restored.socketTimeoutMs)
            assertEquals(host.chunkSizeKb, restored.chunkSizeKb)
            assertEquals(host.autoLoadLimitKb, restored.autoLoadLimitKb)
        } finally {
            System.setProperty("user.home", originalHome)
            tempHome.deleteRecursively()
        }
    }

    @Test
    fun `desktop settings repository keeps satellite root uri consistent`() {
        val originalHome = System.getProperty("user.home")
        val tempHome = Files.createTempDirectory("orbitfs-settings-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)

        try {
            val repo = SettingsRepository(PlatformContext())
            repo.setUsername("Pilot-42")
            repo.setSatelliteRootUri("/tmp/orbitfs-share")
            repo.updateThemeMode("Dark Space")
            repo.setSatellitePort(9123)

            val reloaded = SettingsRepository(PlatformContext())
            assertEquals("Pilot-42", reloaded.username.value)
            assertEquals("/tmp/orbitfs-share", reloaded.satelliteRootUri.value)
            assertEquals("Dark Space", reloaded.themeMode.value)
            assertEquals(9123, reloaded.satellitePort.value)
        } finally {
            System.setProperty("user.home", originalHome)
            tempHome.deleteRecursively()
        }
    }
}
