package org.orbitfs.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.orbitfs.common.ui.AndroidAppShell
import org.orbitfs.common.ui.FileBrowserViewModel
import org.orbitfs.common.client.ConnectionManager
import org.orbitfs.common.data.HostRepository
import org.orbitfs.common.data.SettingsRepository
import org.orbitfs.common.data.AndroidLocalFileRepository
import org.orbitfs.common.client.OrbitRadar
import org.orbitfs.common.util.PlatformContext
import androidx.lifecycle.lifecycleScope
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.os.Environment
import android.Manifest
import android.content.pm.PackageManager
import org.orbitfs.android.service.OrbitFSServerService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                val action = if (settingsRepository.satelliteEnabled.value) {
                    OrbitFSServerService.ACTION_STOP
                } else {
                    OrbitFSServerService.ACTION_START
                }
                val intent = Intent(this, OrbitFSServerService::class.java).apply {
                    this.action = action
                }
                startForegroundService(intent)
            }
        )

        setContent {
            AndroidAppShell(
                viewModel = viewModel,
                settingsRepository = settingsRepository,
                hostRepository = hostRepository
            )
        }
        
        checkPermissions()
    }

    fun checkPermissions() {
        // 1. Manage All Files Permission (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }

        // 2. Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 102)
            }
        }
    }
}
