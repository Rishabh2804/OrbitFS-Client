package org.orbitfs.common.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.orbitfs.android.MainActivity
import org.orbitfs.android.client.NotificationSignals
import org.orbitfs.common.data.SavedHost
import org.orbitfs.common.data.SettingsRepository
import org.orbitfs.common.model.FileInfo
import org.orbitfs.common.model.UiEffect
import org.orbitfs.common.ui.theme.OrbitFSTheme
import org.orbitfs.common.client.ConnectionState
import org.orbitfs.common.data.HostRepository

@Composable
fun AndroidAppShell(
    viewModel: FileBrowserViewModel,
    settingsRepository: SettingsRepository,
    hostRepository: HostRepository
) {
    val context = LocalContext.current
    
    val browserState by viewModel.state.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val pingResults by viewModel.pingResults.collectAsStateWithLifecycle()
    val username by settingsRepository.username.collectAsStateWithLifecycle()
    val avatarId by settingsRepository.avatarId.collectAsStateWithLifecycle()
    val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle()
    val satelliteState by viewModel.satelliteState.collectAsStateWithLifecycle()
    val discoveredOrbiters by viewModel.discoveredOrbiters.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    // Handle UI Effects
    LaunchedEffect(Unit) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                is UiEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is UiEffect.OpenFile -> {
                    val uri = if (effect.file.path.startsWith("content://")) {
                        Uri.parse(effect.file.path)
                    } else {
                        try {
                            FileProvider.getUriForFile(context, "org.orbitfs.android.kmp.fileprovider", effect.file)
                        } catch (_: Exception) {
                            Uri.fromFile(effect.file)
                        }
                    }
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, effect.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val chooser = Intent.createChooser(intent, "Open with")
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(chooser)
                        } catch (e2: Exception) {
                            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                is UiEffect.ShareFile -> {
                    val uri = if (effect.file.path.startsWith("content://")) {
                        Uri.parse(effect.file.path)
                    } else {
                        try {
                            FileProvider.getUriForFile(context, "org.orbitfs.android.kmp.fileprovider", effect.file)
                        } catch (_: Exception) {
                            Uri.fromFile(effect.file)
                        }
                    }
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

    LaunchedEffect(Unit) {
        NotificationSignals.cancelRequest.collect { path ->
            viewModel.cancelDownload(path)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                viewModel.updateSatelliteRoot(uri.toString(), uri.path?.substringAfterLast(":") ?: "Shared Folder")
            }
        }
    )

    // Back Handling
    var backActionLambda by remember { mutableStateOf<(() -> Boolean)?>(null) }

    BackHandler(enabled = backActionLambda != null) {
        val handled = backActionLambda?.invoke() ?: false
        if (!handled) {
            backActionLambda = null 
        }
    }

    var pendingDownloadFile by remember { mutableStateOf<FileInfo?>(null) }
    
    val downloadFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null && pendingDownloadFile != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    viewModel.downloadFile(pendingDownloadFile!!, uri.toString())
                } catch (e: Exception) {
                    Toast.makeText(context, "Permission failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                pendingDownloadFile = null
            }
        }
    )

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
            onRetryDownload = { path -> viewModel.retryDownload(path) },
            onClearHistory = { viewModel.clearTransferHistory() },
            onSaveToDevice = { 
                if (it.name == "DUMMY") {
                    Toast.makeText(context, "Feature coming soon!", Toast.LENGTH_SHORT).show()
                } else {
                    pendingDownloadFile = it
                    downloadFolderPickerLauncher.launch(null)
                }
            },
            onShareFile = { viewModel.shareFile(it) },
            onUpdateActiveHostSettings = { gd, to, cs, al -> viewModel.updateActiveHostSettings(gd, to, cs, al) },
            onDismissLargeDownload = { viewModel.dismissLargeDownload() },
            onToggleSatellite = { viewModel.toggleSatellite() },
            onPickSatelliteFolder = { folderPickerLauncher.launch(null) },
            onUpdateSatelliteConfig = { port, root -> viewModel.updateSatelliteConfig(port, root) },
            onStartRadar = { viewModel.startRadar() },
            onStopRadar = { viewModel.stopRadar() },
            onResetIdentity = { viewModel.resetIdentity() },
            onCheckPermissions = { (context as MainActivity).checkPermissions() },
            onBackIntercept = { backActionLambda = it },
            settingsRepository = settingsRepository,
            onOpenLocalFile = { viewModel.openLocalFile(it) },
            onDeleteHistoryItem = { path, deleteFile -> viewModel.deleteTransferItem(path, deleteFile) }
        )
    }
}
