package org.orbitfs.common.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.orbitfs.common.client.ConnectionManager
import org.orbitfs.common.client.ConnectionState
import org.orbitfs.common.data.ConnectionConfig
import org.orbitfs.common.data.SavedHost
import org.orbitfs.common.data.SettingsRepository
import org.orbitfs.common.model.*
import org.orbitfs.common.ui.components.*
import org.orbitfs.common.ui.screens.*
import org.orbitfs.common.ui.theme.*
import org.orbitfs.common.util.MimeTypeUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedAppContent(
    connectionState: ConnectionState,
    hosts: List<SavedHost>,
    browserState: BrowserState,
    downloadStates: Map<String, FileDownloadState>,
    pingResults: Map<String, Int?>,
    username: String,
    avatarId: String,
    satelliteState: SatelliteState,
    discoveredOrbiters: Set<OrbiterInfo>,
    onAddHost: (String, String, Int, String) -> Unit,
    onUpdateHost: (SavedHost) -> Unit,
    onRemoveHost: (String) -> Unit,
    onConnect: (SavedHost) -> Unit,
    onDisconnect: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (FileInfo) -> Unit,
    onSelectFile: (String) -> Unit,
    onToggleMultiSelect: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onUpdateSort: (SortType, SortOrder) -> Unit,
    onRefresh: () -> Unit,
    onToggleHidden: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSaveToDevice: (FileInfo) -> Unit,
    onShareFile: (FileInfo) -> Unit,
    onUpdateActiveHostSettings: (Boolean, Int, Int, Int) -> Unit,
    onDismissLargeDownload: () -> Unit,
    onToggleSatellite: () -> Unit,
    onPickSatelliteFolder: () -> Unit,
    onUpdateSatelliteConfig: (Int, String?) -> Unit,
    onStartRadar: () -> Unit,
    onStopRadar: () -> Unit,
    onResetIdentity: () -> Unit = {},
    onCheckPermissions: () -> Unit = {},
    onBackIntercept: ((() -> Boolean) -> Unit)? = null,
    settingsRepository: SettingsRepository? = null,
    onOpenLocalFile: (FileDownloadState) -> Unit = {}
) {
    val isConnected = connectionState is ConnectionState.Connected
    
    var hubPageIndex by rememberSaveable { mutableStateOf(1) } 
    var sessionPageIndex by rememberSaveable { mutableStateOf(0) } 
    
    var showAddNodeDialog by rememberSaveable { mutableStateOf(false) }
    var hostToEdit by remember { mutableStateOf<SavedHost?>(null) }
    var selectedFileInfoForDetails by remember { mutableStateOf<FileInfo?>(null) }
    var showSearchModal by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val themeModeFlow = remember(settingsRepository) { settingsRepository?.themeMode ?: MutableStateFlow("System Default") }
    val themeMode by themeModeFlow.collectAsState()
    
    val notificationsEnabledFlow = remember(settingsRepository) { settingsRepository?.notificationsEnabled ?: MutableStateFlow(true) }
    val notificationsEnabled by notificationsEnabledFlow.collectAsState()

    val handleBack = {
        when {
            showSearchModal -> { showSearchModal = false; true }
            browserState.isMultiSelect -> { onClearSelection(); true }
            isConnected -> {
                when {
                    sessionPageIndex > 0 -> { sessionPageIndex = 0; true }
                    browserState.currentPath != "/" && browserState.currentPath.isNotEmpty() -> { onNavigateTo(".."); true }
                    else -> { onDisconnect(); true }
                }
            }
            else -> {
                if (hubPageIndex != 1) { hubPageIndex = 1; true } else false
            }
        }
    }

    DisposableEffect(onBackIntercept, showSearchModal, browserState.isMultiSelect, isConnected, sessionPageIndex, hubPageIndex, browserState.currentPath) {
        onBackIntercept?.invoke(handleBack)
        onDispose { onBackIntercept?.invoke { false } }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isConnected) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = sessionPageIndex == 0,
                        onClick = { sessionPageIndex = 0 },
                        icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                        label = { Text("Explorer") }
                    )
                    NavigationBarItem(
                        selected = sessionPageIndex == 1,
                        onClick = { sessionPageIndex = 1 },
                        icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        label = { Text("Transfers") }
                    )
                    NavigationBarItem(
                        selected = sessionPageIndex == 2,
                        onClick = { sessionPageIndex = 2 },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("Configuration") }
                    )
                }
            } else if (hubPageIndex > 0) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = hubPageIndex == 1,
                        onClick = { hubPageIndex = 1 },
                        icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                        label = { Text("Nodes") }
                    )
                    NavigationBarItem(
                        selected = hubPageIndex == 2,
                        onClick = { hubPageIndex = 2 },
                        icon = { Icon(Icons.Rounded.Radar, contentDescription = null) },
                        label = { Text("Radar") }
                    )
                    NavigationBarItem(
                        selected = hubPageIndex == 3,
                        onClick = { hubPageIndex = 3 },
                        icon = { Icon(Icons.Rounded.Router, contentDescription = null) },
                        label = { Text("Satellite") }
                    )
                    NavigationBarItem(
                        selected = hubPageIndex == 4,
                        onClick = { hubPageIndex = 4 },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (!isConnected) {
                Crossfade(targetState = hubPageIndex, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> ProfileScreen(
                            username = username,
                            avatarId = avatarId,
                            onBack = { hubPageIndex = 1 },
                            onUpdateUsername = { settingsRepository?.setUsername(it) },
                            onUpdateAvatarId = { settingsRepository?.setAvatarId(it) },
                            settingsRepository = settingsRepository
                        )
                        1 -> NodeHubScreen(
                            hosts = hosts,
                            pingResults = pingResults,
                            username = username,
                            avatarId = avatarId,
                            satelliteState = satelliteState,
                            onAddNode = { showAddNodeDialog = true },
                            onEditNode = { hostToEdit = it },
                            onConnect = { onConnect(it) },
                            onRetry = { onConnect(it) },
                            onProfileClick = { hubPageIndex = 0 },
                            onSettingsClick = { hubPageIndex = 4 },
                            onLaunchSatellite = { hubPageIndex = 3 }
                        )
                        2 -> {
                            DisposableEffect(Unit) {
                                onStartRadar()
                                onDispose { onStopRadar() }
                            }
                            RadarScreen(
                                discoveredOrbiters = discoveredOrbiters,
                                savedHosts = hosts,
                                pilotAvatarId = avatarId,
                                onBack = { hubPageIndex = 1 },
                                onOrbiterClick = { info ->
                                    onAddHost(info.name, info.host, info.port, info.nodeId)
                                    hubPageIndex = 1
                                },
                                onRefresh = { 
                                    onStopRadar()
                                    onStartRadar()
                                }
                            )
                        }
                        3 -> SatelliteScreen(
                            state = satelliteState,
                            username = username,
                            pilotAvatarId = avatarId,
                            onBack = { hubPageIndex = 1 },
                            onToggleServer = onToggleSatellite,
                            onPickFolder = onPickSatelliteFolder,
                            onUpdateConfig = onUpdateSatelliteConfig,
                            onOrbiterClick = { info ->
                                onAddHost(info.name, info.host, info.port, info.nodeId)
                                hubPageIndex = 1
                            },
                            onPermissionStatusClick = onCheckPermissions
                        )
                        4 -> SettingsScreen(
                            themeMode = themeMode,
                            notificationsEnabled = notificationsEnabled,
                            onBack = { hubPageIndex = 1 },
                            onUpdateTheme = { settingsRepository?.updateThemeMode(it) },
                            onToggleNotifications = { settingsRepository?.setNotificationsEnabled(it) },
                            onResetIdentity = onResetIdentity,
                            onCheckPermissions = onCheckPermissions
                        )
                    }
                }
            } else {
                Crossfade(targetState = sessionPageIndex, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> {
                            val currentHost = hosts.find { it.host == connectionState.config.host && it.port == connectionState.config.port }
                            FileExplorerScreen(
                                state = browserState,
                                serverName = connectionState.config.name,
                                serverAddress = "${connectionState.config.host}:${connectionState.config.port}",
                                onBack = { onDisconnect() },
                                onSearchClick = { showSearchModal = true },
                                onBreadcrumbClick = { onNavigateTo(it) },
                                onItemClick = { file -> 
                                    if (browserState.isMultiSelect) onSelectFile(file.path)
                                    else if (file.isDirectory) onNavigateTo(file.path)
                                    else onOpenFile(file)
                                },
                                onItemLongClick = { file -> 
                                    if (!browserState.isMultiSelect) onToggleMultiSelect()
                                    onSelectFile(file.path)
                                },
                                onItemSelectToggle = { onSelectFile(it.path) },
                                onClearSelection = { onClearSelection() },
                                onUploadFile = { onSaveToDevice(FileInfo("DUMMY", "", 0, false)) /* Reusing trigger for toast */ },
                                onNewDir = { onSaveToDevice(FileInfo("DUMMY", "", 0, false)) },
                                onViewStat = { selectedFileInfoForDetails = it },
                                onSaveToDevice = { onSaveToDevice(it) },
                                onShareFile = { onShareFile(it) },
                                onDeleteSelected = { 
                                    if (currentHost?.guardedDeletion == true) showDeleteConfirm = true 
                                    else onDeleteSelected()
                                },
                                onSortChange = { t, o -> onUpdateSort(t, o) },
                                onRefresh = { onRefresh() },
                                onToggleHiddenFiles = { onToggleHidden() },
                                onSessionSettings = { sessionPageIndex = 2 }
                            )
                        }
                        1 -> TransfersScreen(
                            downloadStates = downloadStates.values.toList(),
                            onBack = { sessionPageIndex = 0 },
                            onClearHistory = { onClearHistory() },
                            onCancelTransfer = { onCancelDownload(it) },
                            onRetryTransfer = { state -> onRetryDownload(state.path) },
                            onOpenDownloadedFile = onOpenLocalFile
                        )
                        2 -> {
                            val currentHost = hosts.find { it.host == connectionState.config.host && it.port == connectionState.config.port }
                            SessionSettingsPage(
                                currentHost = currentHost,
                                onUpdateSettings = onUpdateActiveHostSettings,
                                onRemoveHost = { id ->
                                    onRemoveHost(id)
                                    onDisconnect()
                                    hubPageIndex = 1
                                },
                                onBack = { sessionPageIndex = 0 }
                            )
                        }
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
                    onAddHost(name, host, port, "")
                    showAddNodeDialog = false
                }
            )
        }

        if (hostToEdit != null) {
            AddEditHostDialog(
                showDialog = true,
                initialName = hostToEdit!!.name,
                initialHost = hostToEdit!!.host,
                initialPort = hostToEdit!!.port.toString(),
                onDismissRequest = { hostToEdit = null },
                onConfirm = { name, host, port ->
                    onUpdateHost(hostToEdit!!.copy(name = name, host = host, port = port))
                    hostToEdit = null
                },
                onDelete = {
                    onRemoveHost(hostToEdit!!.id)
                    hostToEdit = null
                }
            )
        }

        if (connectionState is ConnectionState.Connecting) {
            ConnectionModal(
                serverName = connectionState.config.name,
                host = connectionState.config.host,
                port = connectionState.config.port,
                progress = 0.45f,
                statusMessage = "Handshaking with satellite...",
                onCancel = { onDisconnect() }
            )
        }

        SearchModal(
            allFiles = browserState.files,
            visible = showSearchModal,
            onClose = { showSearchModal = false },
            onResultClick = { file ->
                showSearchModal = false
                if (file.isDirectory) onNavigateTo(file.path)
                else onOpenFile(file)
            },
            onManualPathGo = { 
                showSearchModal = false
                onNavigateTo(it)
            }
        )

        if (selectedFileInfoForDetails != null) {
            FileDetailsPopup(
                fileInfo = selectedFileInfoForDetails!!,
                onClose = { selectedFileInfoForDetails = null }
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Items") },
                text = { Text("Permanently delete ${browserState.selectedPaths.size} item(s) from server?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onDeleteSelected()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (browserState.fileToConfirmLargeDownload != null) {
            val file = browserState.fileToConfirmLargeDownload!!
            AlertDialog(
                onDismissRequest = { onDismissLargeDownload() },
                title = { Text("Large File") },
                text = { Text("This file is large (${MimeTypeUtil.formatFileSize(file.size)}). Direct opening may cause memory pressure. Save to device instead?") },
                confirmButton = {
                    Button(onClick = {
                        onDismissLargeDownload()
                        onSaveToDevice(file)
                    }) {
                        Text("Save to Device")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onDismissLargeDownload() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
