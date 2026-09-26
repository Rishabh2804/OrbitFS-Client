package org.orbitfs.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.orbitfs.common.client.ConnectionManager
import org.orbitfs.common.client.OrbitRadar
import org.orbitfs.common.data.DesktopLocalFileRepository
import org.orbitfs.common.data.HostRepository
import org.orbitfs.common.data.SettingsRepository
import org.orbitfs.common.model.UiEffect
import org.orbitfs.common.ui.FileBrowserViewModel
import org.orbitfs.common.ui.SharedAppContent
import org.orbitfs.common.ui.theme.OrbitFSTheme
import org.orbitfs.common.util.PlatformContext
import org.orbitfs.common.util.OrbitLogger
import org.orbitfs.common.protocol.*
import org.orbitfs.common.server.OrbitServerPatcher
import org.orbitfs.server.*
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JFileChooser
import java.util.function.BiFunction

fun main() = application {
    val scope = remember { CoroutineScope(Dispatchers.Main + SupervisorJob()) }
    val platformContext = remember { PlatformContext() }
    val settingsRepository = remember { SettingsRepository(platformContext) }
    val hostRepository = remember { HostRepository(platformContext) }
    val connectionManager = remember { ConnectionManager(scope) }
    val localFileRepository = remember { DesktopLocalFileRepository() }
    val radar = remember { OrbitRadar(platformContext) }
    
    var desktopServer by remember { mutableStateOf<OrbitServerImpl?>(null) }

    fun stopSatellite() {
        OrbitLogger.d("DesktopMain", "STOPPING SATELLITE...")
        radar.unregisterService()
        scope.launch(Dispatchers.IO) {
            desktopServer?.stop()
            desktopServer = null
            settingsRepository.setSatelliteEnabled(false)
        }
    }

    fun startSatellite() {
        if (desktopServer != null) {
            OrbitLogger.d("DesktopMain", "Stopping existing satellite server before launching new instance")
            radar.unregisterService()
            try {
                desktopServer?.stop()
            } catch (_: Exception) {}
            desktopServer = null
            Thread.sleep(500)
        }

        // FORCE ROOT: /Users
        val rootUri = "/Users"
        settingsRepository.setSatelliteRootUri(rootUri, "Users")

        val port = settingsRepository.satellitePort.value
        val pilotName = settingsRepository.username.value
        val avatarId = settingsRepository.avatarId.value
        val showHidden = settingsRepository.showHiddenFiles.value

        val absolutePath = Paths.get(rootUri).toAbsolutePath().normalize()
        OrbitLogger.d("DesktopMain", "LAUNCHING SATELLITE at $absolutePath (Port $port)")

        radar.registerService(port, pilotName, avatarId, settingsRepository.nodeId.value)

        scope.launch(Dispatchers.IO) {
            try {
                if (!Files.exists(absolutePath)) Files.createDirectories(absolutePath)
                
                val server = OrbitServerImpl(port, absolutePath, !showHidden)
                val sandboxField = OrbitServerImpl::class.java.getDeclaredField("sandbox")
                sandboxField.isAccessible = true
                val sandbox = sandboxField.get(server) as SandboxGuard

                // Aggressive patcher
                OrbitServerPatcher.patch(server, sandbox, !showHidden)

                desktopServer = server
                server.start()
            } catch (e: Exception) {
                OrbitLogger.e("DesktopMain", "SATELLITE FATAL ERROR", e)
            }
        }
        settingsRepository.setSatelliteEnabled(true)
    }

    LaunchedEffect(Unit) {
        if (settingsRepository.satelliteEnabled.value) {
            startSatellite()
        }
    }

    val viewModel = remember {
        FileBrowserViewModel(
            connectionManager = connectionManager,
            hostRepository = hostRepository,
            settingsRepository = settingsRepository,
            localFileRepository = localFileRepository,
            orbitRadar = radar,
            viewModelScope = scope,
            onToggleSatellite = {
                if (settingsRepository.satelliteEnabled.value) stopSatellite() else startSatellite()
            }
        )
    }

    val username by settingsRepository.username.collectAsState()
    val avatarId by settingsRepository.avatarId.collectAsState()
    val satelliteState by viewModel.satelliteState.collectAsState()
    val discoveredOrbiters by viewModel.discoveredOrbiters.collectAsState()
    val browserState by viewModel.state.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val pingResults by viewModel.pingResults.collectAsState()
    val connectionState by connectionManager.connectionState.collectAsState()
    val themeMode by settingsRepository.themeMode.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                is UiEffect.OpenFile -> {
                    localFileRepository.openFile(effect.file, effect.mimeType)
                }
                is UiEffect.ShareFile -> {
                    localFileRepository.shareFile(effect.file, effect.mimeType)
                }
                is UiEffect.OpenFolder -> {
                    localFileRepository.shareFile(effect.file, "")
                }
                is UiEffect.ShowToast -> {
                    println("TOAST: ${effect.message}")
                }
            }
        }
    }

    Window(
        onCloseRequest = {
            stopSatellite()
            connectionManager.disconnect()
            exitApplication()
        },
        title = "OrbitFS Desktop",
    ) {
        OrbitFSTheme(
            darkTheme = when (themeMode) {
                "Dark Space" -> true
                "Light Orbit" -> false
                else -> isSystemInDarkTheme()
            }
        ) {
            SharedAppContent(
                connectionState = connectionState,
                hosts = hostRepository.hosts.collectAsState(emptyList()).value,
                browserState = browserState,
                downloadStates = downloadStates,
                pingResults = pingResults,
                username = username,
                avatarId = avatarId,
                satelliteState = satelliteState,
                discoveredOrbiters = discoveredOrbiters,
                onAddHost = { n, h, p, nid -> viewModel.addHost(n, h, p, nid) },
                onUpdateHost = { viewModel.updateHost(it) },
                onRemoveHost = { viewModel.deleteHost(it) },
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
                onRetryDownload = { viewModel.retryDownload(it) },
                onClearHistory = { viewModel.clearTransferHistory() },
                onSaveToDevice = { viewModel.downloadFile(it) },
                onShareFile = { viewModel.shareFile(it) },
                onUpdateActiveHostSettings = { gd, to, cs, al -> viewModel.updateActiveHostSettings(gd, to, cs, al) },
                onDismissLargeDownload = { viewModel.dismissLargeDownload() },
                onToggleSatellite = { viewModel.toggleSatellite() },
                onPickSatelliteFolder = {
                    val chooser = JFileChooser()
                    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    val result = chooser.showOpenDialog(null)
                    if (result == JFileChooser.APPROVE_OPTION) {
                        viewModel.updateSatelliteRoot(chooser.selectedFile.absolutePath, chooser.selectedFile.name)
                    }
                },
                onUpdateSatelliteConfig = { port, root -> viewModel.updateSatelliteConfig(port, root) },
                onStartRadar = { viewModel.startRadar() },
                onStopRadar = { viewModel.stopRadar() },
                onResetIdentity = { viewModel.resetIdentity() },
                onBackIntercept = { /* N/A */ },
                settingsRepository = settingsRepository,
                onOpenLocalFile = { viewModel.openLocalFile(it) },
                onDeleteHistoryItem = { path, deleteFile -> viewModel.deleteTransferItem(path, deleteFile) },
                onOpenFolder = { viewModel.openFolderForDownloadedFile(it) }
            )
        }
    }
}
