package org.orbitfs.android

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.data.HostRepository
import org.orbitfs.android.ui.FileBrowserViewModel
import org.orbitfs.android.ui.FileBrowserViewModelFactory
import org.orbitfs.android.ui.OrbitFSRoot
import java.io.File

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private lateinit var hostRepository: HostRepository
    private lateinit var connectionManager: ConnectionManager
    private lateinit var viewModel: FileBrowserViewModel

    private var backPressedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hostRepository = HostRepository(File(filesDir, "hosts.json"))
        connectionManager = ConnectionManager(lifecycleScope)
        val factory = FileBrowserViewModelFactory(connectionManager, hostRepository)
        viewModel = factory.create(FileBrowserViewModel::class.java)

        setContent {
            OrbitFSRoot(
                connectionManager = connectionManager,
                hostRepository = hostRepository,
                viewModel = viewModel
            )
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentPath = viewModel.currentPath
                if (currentPath.isNotEmpty() && connectionManager.isConnected()) {
                    viewModel.navigateTo("..")
                } else if (connectionManager.isConnected()) {
                    viewModel.disconnect()
                } else {
                    if (System.currentTimeMillis() - backPressedAt < 2000) {
                        finish()
                    } else {
                        backPressedAt = System.currentTimeMillis()
                        Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionManager.disconnect()
    }
}
