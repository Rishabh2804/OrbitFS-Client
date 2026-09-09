package org.orbitfs.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.data.ConnectionConfig

@Composable
fun OrbitFSClientApp(
    viewModel: FileBrowserViewModel,
    connectionManager: ConnectionManager
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var hostInput by rememberSaveable { mutableStateOf(connectionManager.currentConfig.host) }
    var portInput by rememberSaveable {
        mutableStateOf(connectionManager.currentConfig.port.toString())
    }

    OrbitFSApp(
        state = uiState,
        onConnectClicked = {
            connectionManager.connect(connectionManager.currentConfig)
        },
        onDisconnectClicked = {
            connectionManager.disconnect()
        },
        onNavigate = { path ->
            viewModel.navigateTo(path)
        },
        onRefresh = {
            viewModel.refreshCurrentPath()
        },
        onShowSettings = {
            hostInput = connectionManager.currentConfig.host
            portInput = connectionManager.currentConfig.port.toString()
            showSettingsDialog = true
        }
    )

    if (showSettingsDialog) {
        ConnectionSettingsDialog(
            showDialog = showSettingsDialog,
            currentHost = hostInput,
            currentPort = portInput,
            onDismissRequest = { showSettingsDialog = false },
            onConfirm = { host, port ->
                val newConfig = ConnectionConfig(host = host, port = port)
                connectionManager.connect(newConfig)
                showSettingsDialog = false
            }
        )
    }
}
