package org.orbitfs.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.data.HostRepository
import org.orbitfs.android.data.LocalFileRepository
import org.orbitfs.android.data.SettingsRepository
import org.orbitfs.android.ui.FileBrowserViewModel
import org.orbitfs.android.ui.FileBrowserViewModelFactory
import org.orbitfs.android.ui.OrbitFSRoot
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var hostRepository: HostRepository
    private lateinit var connectionManager: ConnectionManager
    private lateinit var viewModel: FileBrowserViewModel
    private lateinit var localFileRepository: LocalFileRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hostRepository = HostRepository(File(filesDir, "hosts.json"))
        val settingsRepo = SettingsRepository(applicationContext)
        localFileRepository = LocalFileRepository(applicationContext)
        connectionManager = ConnectionManager(lifecycleScope)
        
        val factory = FileBrowserViewModelFactory(
            connectionManager,
            hostRepository,
            settingsRepo,
            localFileRepository
        )
        viewModel = factory.create(FileBrowserViewModel::class.java)

        checkPermissions()

        setContent {
            OrbitFSRoot(
                viewModel = viewModel,
                connectionManager = connectionManager,
                hostRepository = hostRepository,
                settingsRepository = settingsRepo
            )
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::connectionManager.isInitialized) {
            connectionManager.disconnect()
        }
    }
}
