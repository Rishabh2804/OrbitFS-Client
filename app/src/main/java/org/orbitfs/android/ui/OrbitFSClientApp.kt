package org.orbitfs.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.nsd.NsdServiceInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.client.ConnectionState
import org.orbitfs.android.data.ConnectionConfig
import org.orbitfs.android.data.HostRepository
import org.orbitfs.android.data.SavedHost
import org.orbitfs.android.data.SettingsRepository
import org.orbitfs.android.model.BrowserState
import org.orbitfs.android.model.FileDownloadState
import org.orbitfs.android.model.FileInfo
import org.orbitfs.android.model.SatelliteState
import org.orbitfs.android.model.SortOrder
import org.orbitfs.android.model.SortType
import org.orbitfs.android.model.UiEffect
import org.orbitfs.android.ui.components.*
import org.orbitfs.android.ui.screens.*
import org.orbitfs.android.ui.theme.*
import org.orbitfs.android.util.MimeTypeUtil

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
    val username by settingsRepository.username.collectAsStateWithLifecycle()
    val avatarId by settingsRepository.avatarId.collectAsStateWithLifecycle()
    val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle()
    val satelliteState by viewModel.satelliteState.collectAsStateWithLifecycle()
    val discoveredOrbiters by viewModel.discoveredOrbiters.collectAsStateWithLifecycle()

    val context = LocalContext.current
    
    val darkTheme = when (themeMode) {
        "Dark Space" -> true
        "Light Orbit" -> false
        else -> isSystemInDarkTheme()
    }

    OrbitFSTheme(darkTheme = darkTheme) {
        OrbitFSRootContent(
            connectionState = connectionState,
            hosts = hosts,
            browserState = browserState,
            downloadStates = downloadStates,
            pingResults = pingResults,
            username = username,
            avatarId = avatarId,
            onAddHost = { name, host, port -> viewModel.addHost(name, host, port) },
            onUpdateHost = { id, name, host, port, gd, to, cs, al -> 
                viewModel.updateHost(id, name, host, port, gd, to, cs, al) 
            },
            onRemoveHost = { hostRepository.removeHost(it) },
            onConnect = { viewModel.connectToSavedHost(it) },
            onDisconnect = { viewModel.disconnect() },
            onNavigateTo = { viewModel.navigateTo(it) },
            onOpenFile = { viewModel.openFile(it) },
            onSelectFile = { viewModel.selectFile(it) },
            onToggleMultiSelect = { viewModel.toggleMultiSelect() },
            onClearSelection = { viewModel.clearSelection() },
            onDeleteSelected = { viewModel.deleteSelectedFiles() },
            onUpdateSort = { t, o -> viewModel.updateSort(t, o) },
            onRefresh = { viewModel.refreshCurrentPath() },
            onToggleHidden = { viewModel.toggleHiddenFiles() },
            onCancelDownload = { viewModel.cancelDownload(it) },
            onRetryDownload = { path -> viewModel.retryDownload(path) },
            onClearHistory = { viewModel.clearTransferHistory() },
            onSaveToDevice = { viewModel.saveToDownloadsWithDestination(it.first, it.second) },
            onShareFile = { viewModel.shareFile(it) },
            onUpdateActiveHostSettings = { gd, to, cs, al -> viewModel.updateActiveHostSettings(gd, to, cs, al) },
            onDismissLargeDownload = { viewModel.dismissLargeDownload() },
            satelliteState = satelliteState,
            discoveredOrbiters = discoveredOrbiters,
            onToggleSatellite = { ctx -> viewModel.toggleSatellite(ctx) },
            onUpdateSatelliteRoot = { uri, path -> viewModel.updateSatelliteRoot(uri, path) },
            onUpdateSatellitePort = { viewModel.updateSatellitePort(it) },
            onStartRadar = { viewModel.startRadar() },
            onStopRadar = { viewModel.stopRadar() },
            uiEffects = viewModel.uiEffects,
            settingsRepository = settingsRepository
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitFSRootContent(
    connectionState: ConnectionState,
    hosts: List<SavedHost>,
    browserState: BrowserState,
    downloadStates: Map<String, FileDownloadState>,
    pingResults: Map<String, Int?>,
    username: String,
    avatarId: String,
    onAddHost: (String, String, Int) -> Unit,
    onUpdateHost: (String, String, String, Int, Boolean, Int, Int, Int) -> Unit,
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
    onSaveToDevice: (Pair<Uri, FileInfo>) -> Unit,
    onShareFile: (FileInfo) -> Unit,
    onUpdateActiveHostSettings: (Boolean, Int, Int, Int) -> Unit,
    onDismissLargeDownload: () -> Unit,
    satelliteState: SatelliteState,
    discoveredOrbiters: Set<NsdServiceInfo>,
    onToggleSatellite: (Context) -> Unit,
    onUpdateSatelliteRoot: (Uri, String) -> Unit,
    onUpdateSatellitePort: (Int) -> Unit,
    onStartRadar: () -> Unit,
    onStopRadar: () -> Unit,
    uiEffects: Flow<UiEffect>? = null,
    settingsRepository: SettingsRepository? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val pathDisplay = uri.path?.substringAfterLast(":") ?: "Shared Folder"
                onUpdateSatelliteRoot(uri, pathDisplay)
            }
        }
    )

    // Handle UI Effects
    LaunchedEffect(Unit) {
        uiEffects?.collect { effect ->
            when (effect) {
                is UiEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is UiEffect.OpenFile -> {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", effect.file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, effect.mimeType)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    try { context.startActivity(intent) } catch (e: Exception) {
                        Toast.makeText(context, "No app to open this file type", Toast.LENGTH_SHORT).show()
                    }
                }
                is UiEffect.ShareFile -> {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", effect.file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = effect.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share File"))
                }
            }
        }
    }
    
    val isConnected = connectionState is ConnectionState.Connected
    
    // Hub Pager Order: [0: Profile, 1: Nodes, 2: Radar, 3: Satellite, 4: Settings]
    val hubPagerState = rememberPagerState(initialPage = 1, pageCount = { 5 })
    val sessionPagerState = rememberPagerState(pageCount = { 3 })
    
    var showAddNodeDialog by rememberSaveable { mutableStateOf(false) }
    var hostToEdit by remember { mutableStateOf<SavedHost?>(null) }
    var selectedFileInfoForDetails by remember { mutableStateOf<FileInfo?>(null) }
    var showSearchModal by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingFilePathForPicker by rememberSaveable { mutableStateOf<String?>(null) }

    val saveWithDestinationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri != null) {
                val fileToDownload = if (pendingFilePathForPicker != null) {
                    browserState.files.find { it.path == pendingFilePathForPicker }
                } else {
                    browserState.files.find { it.path in browserState.selectedPaths }
                }
                
                if (fileToDownload != null) {
                    onSaveToDevice(uri to fileToDownload)
                }
            }
            pendingFilePathForPicker = null
        }
    )

    BackHandler(enabled = true) {
        if (showSearchModal) {
            showSearchModal = false
        } else if (browserState.isMultiSelect) {
            onClearSelection()
        } else if (isConnected) {
            if (sessionPagerState.currentPage > 0) {
                scope.launch { sessionPagerState.animateScrollToPage(0) }
            } else if (browserState.currentPath.isNotEmpty()) {
                onNavigateTo("..")
            } else {
                onDisconnect()
            }
        } else {
            if (hubPagerState.currentPage != 1) {
                scope.launch { hubPagerState.animateScrollToPage(1) }
            } else {
                // OS handles exit
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isConnected) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = sessionPagerState.currentPage == 0,
                        onClick = { scope.launch { sessionPagerState.animateScrollToPage(0) } },
                        icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                        label = { Text("Explorer") }
                    )
                    NavigationBarItem(
                        selected = sessionPagerState.currentPage == 1,
                        onClick = { scope.launch { sessionPagerState.animateScrollToPage(1) } },
                        icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        label = { Text("Transfers") }
                    )
                    NavigationBarItem(
                        selected = sessionPagerState.currentPage == 2,
                        onClick = { scope.launch { sessionPagerState.animateScrollToPage(2) } },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("Configuration") }
                    )
                }
            } else if (hubPagerState.currentPage > 0) { // Only show bottom nav for main pages (1-4)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = hubPagerState.currentPage == 1,
                        onClick = { scope.launch { hubPagerState.animateScrollToPage(1) } },
                        icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                        label = { Text("Nodes") }
                    )
                    NavigationBarItem(
                        selected = hubPagerState.currentPage == 2,
                        onClick = { scope.launch { hubPagerState.animateScrollToPage(2) } },
                        icon = { Icon(Icons.Rounded.Radar, contentDescription = null) },
                        label = { Text("Radar") }
                    )
                    NavigationBarItem(
                        selected = hubPagerState.currentPage == 3,
                        onClick = { scope.launch { hubPagerState.animateScrollToPage(3) } },
                        icon = { Icon(Icons.Rounded.Router, contentDescription = null) },
                        label = { Text("Satellite") }
                    )
                    NavigationBarItem(
                        selected = hubPagerState.currentPage == 4,
                        onClick = { scope.launch { hubPagerState.animateScrollToPage(4) } },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (!isConnected) {
                HorizontalPager(
                    state = hubPagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = false // Disable swipe on home screen
                ) { page ->
                    when (page) {
                        0 -> {
                            if (settingsRepository != null) {
                                ProfileScreen(
                                    settingsRepository = settingsRepository,
                                    onBack = { scope.launch { hubPagerState.animateScrollToPage(1) } }
                                )
                            }
                        }
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
                            onProfileClick = { scope.launch { hubPagerState.animateScrollToPage(0) } },
                            onSettingsClick = { scope.launch { hubPagerState.animateScrollToPage(4) } },
                            onLaunchSatellite = { scope.launch { hubPagerState.animateScrollToPage(3) } }
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
                                onBack = { scope.launch { hubPagerState.animateScrollToPage(1) } },
                                onOrbiterClick = { info ->
                                    val name = info.serviceName.removePrefix("OrbitFS-")
                                    val host = info.host?.hostAddress ?: ""
                                    if (host.isNotEmpty()) {
                                        onAddHost(name, host, info.port)
                                        scope.launch { hubPagerState.animateScrollToPage(1) }
                                    }
                                }
                            )
                        }
                        3 -> {
                            SatelliteScreen(
                                state = satelliteState,
                                discoveredOrbiters = discoveredOrbiters,
                                onBack = { scope.launch { hubPagerState.animateScrollToPage(1) } },
                                onToggleServer = { onToggleSatellite(context) },
                                onPickFolder = { folderPickerLauncher.launch(null) },
                                onUpdatePort = onUpdateSatellitePort,
                                onOrbiterClick = { /* Handled in radar */ }
                            )
                        }
                        4 -> {
                            if (settingsRepository != null) {
                                SettingsScreen(
                                    settingsRepository = settingsRepository,
                                    onBack = { scope.launch { hubPagerState.animateScrollToPage(1) } },
                                    onClearCache = { Toast.makeText(context, "Cache purged", Toast.LENGTH_SHORT).show() }
                                )
                            }
                        }
                    }
                }
            } else {
                HorizontalPager(
                    state = sessionPagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = sessionPagerState.currentPage != 1
                ) { page ->
                    when (page) {
                        0 -> {
                            val config = connectionState.config
                            val currentHost = hosts.find { it.host == config.host && it.port == config.port }
                            
                            FileExplorerScreen(
                                state = browserState,
                                serverName = config.name,
                                serverAddress = "${config.host}:${config.port}",
                                onBack = { onDisconnect() },
                                onSearchClick = { showSearchModal = true },
                                onBreadcrumbClick = { onNavigateTo(it) },
                                onItemClick = { file -> 
                                    if (browserState.isMultiSelect) onSelectFile(file.path)
                                    else if (file.isDirectory) onNavigateTo(file.path)
                                    else onOpenFile(file)
                                },
                                onItemLongClick = { file -> 
                                    onSelectFile(file.path)
                                    if (!browserState.isMultiSelect) onToggleMultiSelect()
                                },
                                onItemSelectToggle = { onSelectFile(it.path) },
                                onClearSelection = { onClearSelection() },
                                onUploadFile = { },
                                onNewDir = { },
                                onViewStat = { selectedFileInfoForDetails = it },
                                onSaveToDevice = { 
                                    pendingFilePathForPicker = it.path
                                    saveWithDestinationLauncher.launch(it.displayName) 
                                },
                                onShareFile = { onShareFile(it) },
                                onDeleteSelected = { 
                                    if (currentHost?.guardedDeletion == true) showDeleteConfirm = true 
                                    else onDeleteSelected()
                                },
                                onSortChange = { t, o -> onUpdateSort(t, o) },
                                onRefresh = { onRefresh() },
                                onToggleHiddenFiles = { onToggleHidden() },
                                onSessionSettings = { scope.launch { sessionPagerState.animateScrollToPage(2) } }
                            )
                        }
                        1 -> TransfersScreen(
                            downloadStates = downloadStates.values.toList(),
                            onBack = { scope.launch { sessionPagerState.animateScrollToPage(0) } },
                            onClearHistory = { onClearHistory() },
                            onCancelTransfer = { onCancelDownload(it) },
                            onRetryTransfer = { state -> onRetryDownload(state.path) }
                        )
                        2 -> {
                            val currentHost = hosts.find { it.host == connectionState.config.host && it.port == connectionState.config.port }
                            SessionSettingsPage(
                                currentHost = currentHost,
                                onUpdateSettings = onUpdateActiveHostSettings,
                                onBack = { scope.launch { sessionPagerState.animateScrollToPage(0) } }
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
                    onAddHost(name, host, port)
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
                    onUpdateHost(hostToEdit!!.id, name, host, port, 
                        hostToEdit!!.guardedDeletion, hostToEdit!!.socketTimeoutMs, hostToEdit!!.chunkSizeKb, hostToEdit!!.autoLoadLimitKb)
                    hostToEdit = null
                },
                onDelete = {
                    onRemoveHost(hostToEdit!!.id)
                    hostToEdit = null
                }
            )
        }

        if (connectionState is ConnectionState.Connecting) {
            val config = connectionState.config
            ConnectionModal(
                serverName = config.name,
                host = config.host,
                port = config.port,
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
                fileInfo = selectedFileInfoForDetails,
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
            val file = browserState.fileToConfirmLargeDownload
            AlertDialog(
                onDismissRequest = { onDismissLargeDownload() },
                title = { Text("Large File") },
                text = { Text("This file is large (${MimeTypeUtil.formatFileSize(file.size)}). Direct opening may cause memory pressure. Save to device instead?") },
                confirmButton = {
                    Button(onClick = {
                        pendingFilePathForPicker = file.path
                        onDismissLargeDownload()
                        saveWithDestinationLauncher.launch(file.displayName)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSettingsPage(
    currentHost: SavedHost?,
    onUpdateSettings: (Boolean, Int, Int, Int) -> Unit,
    onBack: () -> Unit
) {
    val host = currentHost ?: return

    var isGuarded by remember { mutableStateOf(host.guardedDeletion) }
    var currentTimeout by remember { mutableIntStateOf(host.socketTimeoutMs) }
    var currentChunkSize by remember { mutableIntStateOf(host.chunkSizeKb) }
    var currentAutoLoad by remember { mutableIntStateOf(host.autoLoadLimitKb) }

    var showThresholdDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showChunkSizeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsGroup(title = "Safety", icon = Icons.Rounded.Settings) {
                SettingsRow(
                    title = "Guarded Deletion",
                    subtitle = "Always prompt before deleting items",
                    onClick = { 
                        isGuarded = !isGuarded
                        onUpdateSettings(isGuarded, currentTimeout, currentChunkSize, currentAutoLoad)
                    },
                    control = {
                        Switch(
                            checked = isGuarded,
                            onCheckedChange = { 
                                isGuarded = it
                                onUpdateSettings(isGuarded, currentTimeout, currentChunkSize, currentAutoLoad)
                            }
                        )
                    }
                )
            }

            SettingsGroup(title = "Network Tuning", icon = Icons.Rounded.Settings) {
                SettingsRow(
                    title = "Socket Timeout",
                    subtitle = "Connection drop threshold",
                    onClick = { showTimeoutDialog = true },
                    control = { Text("${currentTimeout}ms", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    title = "Chunk Buffer Size",
                    subtitle = "Memory per frame",
                    onClick = { showChunkSizeDialog = true },
                    control = { Text("$currentChunkSize KB", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    title = "Auto-load Limit",
                    subtitle = "Pre-load small files",
                    onClick = { showThresholdDialog = true },
                    control = { Text("${currentAutoLoad}KB", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }

        if (showThresholdDialog) {
            NumberInputDialog(
                title = "Auto-load Limit",
                initialValue = currentAutoLoad.toString(),
                unit = "KB",
                onDismiss = { showThresholdDialog = false },
                onConfirm = {
                    it.toIntOrNull()?.let { valKb -> 
                        currentAutoLoad = valKb
                        onUpdateSettings(isGuarded, currentTimeout, currentChunkSize, currentAutoLoad)
                    }
                    showThresholdDialog = false
                }
            )
        }

        if (showTimeoutDialog) {
            NumberInputDialog(
                title = "Socket Timeout",
                initialValue = currentTimeout.toString(),
                unit = "ms",
                onDismiss = { showTimeoutDialog = false },
                onConfirm = {
                    it.toIntOrNull()?.let { valMs -> 
                        currentTimeout = valMs
                        onUpdateSettings(isGuarded, currentTimeout, currentChunkSize, currentAutoLoad)
                    }
                    showTimeoutDialog = false
                }
            )
        }

        if (showChunkSizeDialog) {
            NumberInputDialog(
                title = "Chunk Size",
                initialValue = currentChunkSize.toString(),
                unit = "KB",
                onDismiss = { showChunkSizeDialog = false },
                onConfirm = {
                    it.toIntOrNull()?.let { valKb -> 
                        currentChunkSize = valKb
                        onUpdateSettings(isGuarded, currentTimeout, currentChunkSize, currentAutoLoad)
                    }
                    showChunkSizeDialog = false
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OrbitFSRootPreview() {
     OrbitFSRootContent(
        connectionState = ConnectionState.Connected(ConnectionConfig("Mars Base", "127.0.0.1", 9090)),
        hosts = listOf(
            SavedHost("1", "Mars Base", "127.0.0.1", 9090),
            SavedHost("2", "Earth Relay", "192.168.1.1", 8080)
        ),
        browserState = BrowserState(
            currentPath = "/root/data",
            files = listOf(
                FileInfo("sensor_log.txt", "/root/data/sensor_log.txt", 1024, false),
                FileInfo("telemetry", "/root/data/telemetry", 0, true)
            )
        ),
        downloadStates = emptyMap(),
        pingResults = mapOf("1" to 20, "2" to null),
        username = "Pilot-Preview",
        avatarId = "rocket",
        onAddHost = { _, _, _ -> },
        onUpdateHost = { _, _, _, _, _, _, _, _ -> },
        onRemoveHost = { },
        onConnect = { },
        onDisconnect = { },
        onNavigateTo = { },
        onOpenFile = { },
        onSelectFile = { },
        onToggleMultiSelect = { },
        onClearSelection = { },
        onDeleteSelected = { },
        onUpdateSort = { _, _ -> },
        onRefresh = { },
        onToggleHidden = { },
        onCancelDownload = { },
        onRetryDownload = { },
        onClearHistory = { },
        onSaveToDevice = { },
        onShareFile = { },
        onUpdateActiveHostSettings = { _, _, _, _ -> },
        onDismissLargeDownload = { },
        satelliteState = SatelliteState(),
        discoveredOrbiters = emptySet(),
        onToggleSatellite = { },
        onUpdateSatelliteRoot = { _, _ -> },
        onUpdateSatellitePort = { },
        onStartRadar = { },
        onStopRadar = { },
        settingsRepository = null
    )
}
