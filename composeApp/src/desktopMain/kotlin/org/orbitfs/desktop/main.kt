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
import org.orbitfs.common.ui.FileBrowserViewModel
import org.orbitfs.common.ui.SharedAppContent
import org.orbitfs.common.ui.theme.OrbitFSTheme
import org.orbitfs.common.util.PlatformContext
import org.orbitfs.common.protocol.*
import org.orbitfs.server.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JFileChooser
import java.util.function.BiFunction
import kotlin.io.path.name
import kotlin.io.path.isDirectory
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import java.util.stream.Collectors

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
        radar.unregisterService()
        scope.launch(Dispatchers.IO) {
            desktopServer?.stop()
            desktopServer = null
        }
        settingsRepository.setSatelliteEnabled(false)
    }

    fun startSatellite() {
        val rootUri = settingsRepository.satelliteRootUri.value
            ?: File(System.getProperty("user.home"), "OrbitFS_Shared").apply {
                mkdirs()
                val sampleDocs = File(this, "SampleDesktopDocs").apply { mkdirs() }
                val sampleFile = File(sampleDocs, "welcome.txt")
                if (!sampleFile.exists()) {
                    sampleFile.writeText("Welcome to OrbitFS on Desktop!\nLocal peer-to-peer file sharing is active.\n")
                }
            }.absolutePath
        settingsRepository.setSatelliteRootUri(rootUri)

        val port = settingsRepository.satellitePort.value
        val pilotName = settingsRepository.username.value
        val avatarId = settingsRepository.avatarId.value
        val showHidden = settingsRepository.showHiddenFiles.value

        val absolutePath = Paths.get(rootUri).toAbsolutePath().normalize()
        println("SATELLITE: Launching on Mac at $absolutePath")

        radar.registerService(port, pilotName, avatarId, settingsRepository.nodeId.value)

        scope.launch(Dispatchers.IO) {
            try {
                val server = OrbitServerImpl(port, absolutePath, !showHidden)
                val sandboxField = OrbitServerImpl::class.java.getDeclaredField("sandbox")
                sandboxField.isAccessible = true
                val sandbox = sandboxField.get(server) as SandboxGuard

                org.orbitfs.common.server.OrbitServerPatcher.patch(server, sandbox, !showHidden)

                desktopServer = server
                server.start()
            } catch (e: Exception) {
                println("SATELLITE ERROR: ${e.message}")
                e.printStackTrace()
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
                if (settingsRepository.satelliteEnabled.value) {
                    stopSatellite()
                } else {
                    startSatellite()
                }
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
                onAddHost = { n, h, p -> viewModel.addHost(n, h, p) },
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
                onUpdateSatellitePort = { viewModel.updateSatellitePort(it) },
                onStartRadar = { viewModel.startRadar() },
                onStopRadar = { viewModel.stopRadar() },
                onBackIntercept = { /* Desktop back N/A */ },
                settingsRepository = settingsRepository
            )
        }
    }
}
