package org.orbitfs.android.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.client.ConnectionState
import org.orbitfs.android.data.HostRepository
import org.orbitfs.android.data.SavedHost
import org.orbitfs.android.data.SettingsRepository
import org.orbitfs.android.model.BrowserState
import org.orbitfs.android.model.FileInfo
import org.orbitfs.android.ui.components.*
import org.orbitfs.android.ui.screens.*
import org.orbitfs.android.ui.theme.*

enum class ScreenType { NodeHub, Browser, Transfers, Settings }

@Composable
fun OrbitFSRoot(
    viewModel: FileBrowserViewModel,
    connectionManager: ConnectionManager,
    hostRepository: HostRepository,
    settingsRepository: SettingsRepository
) {
    val connectionState by connectionManager.connectionState.collectAsStateWithLifecycle()
    val hosts by hostRepository.hosts.collectAsStateWithLifecycle()
    val browserState by viewModel.state.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val pingResults by viewModel.pingResults.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var currentScreen by rememberSaveable { mutableStateOf(ScreenType.NodeHub) }
    
    // Dialog States
    var showAddNodeDialog by rememberSaveable { mutableStateOf(false) }
    var selectedFileInfoForDetails by remember { mutableStateOf<FileInfo?>(null) }
    var showSearchModal by rememberSaveable { mutableStateOf(false) }

    // Save destination picker
    val saveWithDestinationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri != null) {
                val selectedFiles = browserState.files.filter { it.path in browserState.selectedPaths }
                if (selectedFiles.isNotEmpty()) {
                    viewModel.saveToDownloadsWithDestination(uri, selectedFiles.first(), context)
                }
            }
        }
    )

    // Handle Back Press Logic
    BackHandler(enabled = true) {
        if (showSearchModal) {
            showSearchModal = false
        } else if (browserState.isMultiSelect) {
            viewModel.clearSelection()
        } else if (currentScreen != ScreenType.Browser && connectionState is ConnectionState.Connected) {
            currentScreen = ScreenType.Browser
        } else if (currentScreen == ScreenType.Browser && browserState.currentPath.isNotEmpty()) {
            viewModel.navigateTo("..")
        } else if (connectionState is ConnectionState.Connected) {
            viewModel.disconnect()
        } else {
            // Let system handle back (close app)
        }
    }

    // Auto-navigation based on connection
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected && currentScreen == ScreenType.NodeHub) {
            currentScreen = ScreenType.Browser
        } else if (connectionState is ConnectionState.Disconnected) {
            currentScreen = ScreenType.NodeHub
        }
    }

    OrbitFSTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (connectionState is ConnectionState.Connected) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == ScreenType.Browser,
                            onClick = { currentScreen = ScreenType.Browser },
                            icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                            label = { Text("Explorer") }
                        )
                        NavigationBarItem(
                            selected = currentScreen == ScreenType.Transfers,
                            onClick = { currentScreen = ScreenType.Transfers },
                            icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                            label = { Text("Transfers") }
                        )
                        NavigationBarItem(
                            selected = currentScreen == ScreenType.Settings,
                            onClick = { currentScreen = ScreenType.Settings },
                            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            label = { Text("Settings") }
                        )
                    }
                }
            }
        ) { paddingValues ->
            AnimatedContent(
                targetState = currentScreen,
                modifier = Modifier.padding(paddingValues),
                transitionSpec = {
                    fadeIn() + scaleIn(initialScale = 0.95f) togetherWith fadeOut() + scaleOut(targetScale = 1.05f)
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    ScreenType.NodeHub -> {
                        NodeHubScreen(
                            hosts = hosts,
                            pingResults = pingResults,
                            onAddNode = { showAddNodeDialog = true },
                            onConnect = { host -> viewModel.connectToSavedHost(host) },
                            onRetry = { host -> viewModel.connectToSavedHost(host) },
                            onProfileClick = { Toast.makeText(context, "Cloud sync enabled", Toast.LENGTH_SHORT).show() }
                        )
                    }
                    ScreenType.Browser -> {
                        val config = connectionManager.currentConfig
                        FileExplorerScreen(
                            state = browserState,
                            serverName = config.host,
                            serverAddress = "${config.host}:${config.port}",
                            onBack = { viewModel.disconnect() },
                            onSearchClick = { showSearchModal = true },
                            onBreadcrumbClick = { path -> viewModel.navigateTo(path) },
                            onItemClick = { file -> 
                                if (file.isDirectory) viewModel.navigateTo(file.path)
                                else viewModel.openFile(file, context)
                            },
                            onItemLongClick = { file -> 
                                viewModel.selectFile(file.path)
                                viewModel.toggleMultiSelect()
                            },
                            onItemSelectToggle = { file -> viewModel.selectFile(file.path) },
                            onClearSelection = { viewModel.clearSelection() },
                            onUploadFile = { Toast.makeText(context, "Upload coming soon", Toast.LENGTH_SHORT).show() },
                            onNewDir = { Toast.makeText(context, "New folder coming soon", Toast.LENGTH_SHORT).show() },
                            onViewStat = { file -> selectedFileInfoForDetails = file },
                            onSaveToDevice = { file -> saveWithDestinationLauncher.launch(file.displayName) },
                            onShareFile = { file -> viewModel.shareFile(file, context) },
                            onDeleteSelected = { viewModel.deleteSelectedFiles() }
                        )
                    }
                    ScreenType.Transfers -> {
                        TransfersScreen(
                            downloadStates = downloadStates.values.toList(),
                            onBack = { currentScreen = ScreenType.Browser },
                            onClearHistory = { /* Implement clear history logic */ },
                            onCancelTransfer = { path -> viewModel.cancelDownload(path) },
                            onRetryTransfer = { state -> 
                                val file = browserState.files.find { it.path == state.path }
                                if (file != null) viewModel.downloadFile(file, context)
                            }
                        )
                    }
                    ScreenType.Settings -> {
                        SettingsScreen(
                            onBack = { currentScreen = ScreenType.Browser },
                            onClearCache = { Toast.makeText(context, "Cache purged", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }
            }
        }

        // Overlays
        if (showAddNodeDialog) {
            AddEditHostDialog(
                showDialog = showAddNodeDialog,
                onDismissRequest = { showAddNodeDialog = false },
                onConfirm = { name, host, port ->
                    viewModel.addHost(name, host, port)
                    showAddNodeDialog = false
                }
            )
        }

        if (connectionState is ConnectionState.Connecting) {
            val config = (connectionState as ConnectionState.Connecting).config
            ConnectionModal(
                serverName = config.host,
                host = config.host,
                port = config.port,
                progress = 0.45f,
                statusMessage = "Synchronizing node state...",
                onCancel = { viewModel.disconnect() }
            )
        }

        SearchModal(
            visible = showSearchModal,
            allFiles = browserState.files,
            onClose = { showSearchModal = false },
            onResultClick = { file ->
                showSearchModal = false
                if (file.isDirectory) viewModel.navigateTo(file.path)
                else viewModel.openFile(file, context)
            },
            onManualPathGo = { path ->
                showSearchModal = false
                viewModel.navigateTo(path)
            }
        )

        if (selectedFileInfoForDetails != null) {
            FileDetailsPopup(
                fileInfo = selectedFileInfoForDetails,
                onClose = { selectedFileInfoForDetails = null }
            )
        }
    }
}
