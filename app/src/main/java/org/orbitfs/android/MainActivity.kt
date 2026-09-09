package org.orbitfs.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.data.ConnectionConfig
import org.orbitfs.android.data.SettingsRepository
import org.orbitfs.android.ui.FileBrowserViewModel
import org.orbitfs.android.ui.FileBrowserViewModelFactory
import org.orbitfs.android.ui.OrbitFSClientApp

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var connectionManager: ConnectionManager
    private lateinit var viewModel: FileBrowserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsRepository = SettingsRepository()
        connectionManager = ConnectionManager(settingsRepository, lifecycleScope)
        val factory = FileBrowserViewModelFactory(connectionManager)
        viewModel = factory.create(FileBrowserViewModel::class.java)

        setContent {
            OrbitFSClientApp(viewModel, connectionManager)
        }
    }

    override fun onStart() {
        super.onStart()
        val config = settingsRepository.currentConfig
        if (!connectionManager.isConnected()) {
            connectionManager.connect(config)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop - keeping connection alive for quick resume")
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionManager.disconnect()
    }
}
