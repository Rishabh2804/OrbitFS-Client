package org.orbitfs.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import org.orbitfs.android.service.OrbitFSServerService
import org.orbitfs.common.client.ConnectionManager
import org.orbitfs.common.client.OrbitRadar
import org.orbitfs.common.data.AndroidLocalFileRepository
import org.orbitfs.common.data.HostRepository
import org.orbitfs.common.data.SettingsRepository
import org.orbitfs.common.ui.AndroidAppShell
import org.orbitfs.common.ui.FileBrowserViewModel
import org.orbitfs.common.util.PlatformContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val platformContext = PlatformContext(applicationContext)
        val settingsRepository = SettingsRepository(platformContext)
        val hostRepository = HostRepository(platformContext)
        val connectionManager = ConnectionManager(lifecycleScope)
        val localFileRepository = AndroidLocalFileRepository(platformContext)
        val radar = OrbitRadar(platformContext)

        val viewModel = FileBrowserViewModel(
            connectionManager = connectionManager,
            hostRepository = hostRepository,
            settingsRepository = settingsRepository,
            localFileRepository = localFileRepository,
            orbitRadar = radar,
            viewModelScope = lifecycleScope,
            onToggleSatellite = {
                val intent = Intent(this, OrbitFSServerService::class.java).apply {
                    action = if (settingsRepository.satelliteEnabled.value) 
                        OrbitFSServerService.ACTION_STOP 
                    else 
                        OrbitFSServerService.ACTION_START
                }
                if (!settingsRepository.satelliteEnabled.value) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                // The service will update settingsRepository.satelliteEnabled
            }
        )

        setContent {
            AndroidAppShell(
                viewModel = viewModel,
                settingsRepository = settingsRepository,
                hostRepository = hostRepository
            )
        }
    }
}
