package org.orbitfs.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.collection.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.client.ConnectionState
import org.orbitfs.android.client.NotificationSignals
import org.orbitfs.android.client.NsdHelper
import org.orbitfs.android.client.SatelliteServerLauncher
import org.orbitfs.android.data.ConnectionConfig
import org.orbitfs.android.data.HostRepository
import org.orbitfs.android.data.LocalFileRepository
import org.orbitfs.android.data.SavedHost
import org.orbitfs.android.data.SettingsRepository
import org.orbitfs.android.model.BrowserState
import org.orbitfs.android.model.DownloadStatus
import org.orbitfs.android.model.FileDownloadState
import org.orbitfs.android.model.FileInfo
import org.orbitfs.android.model.SatelliteState
import org.orbitfs.android.model.SortOrder
import org.orbitfs.android.model.SortType
import org.orbitfs.android.model.UiEffect
import org.orbitfs.android.service.OrbitFSServerService
import org.orbitfs.common.model.OrbiterInfo
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class FileBrowserViewModel(
    private val connectionManager: ConnectionManager,
    private val hostRepository: HostRepository,
    private val settingsRepository: SettingsRepository,
    private val localFileRepository: LocalFileRepository
) : ViewModel() {

    private val downloadJobs = mutableMapOf<String, Job>()
    private val fileCache = object : LruCache<String, ByteArray>(50 * 1024 * 1024) {}

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state

    private val _downloadStates = MutableStateFlow<Map<String, FileDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, FileDownloadState>> = _downloadStates

    private val _pingResults = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val pingResults: StateFlow<Map<String, Int?>> = _pingResults

    private val _uiEffects = Channel<UiEffect>(Channel.BUFFERED)
    val uiEffects = _uiEffects.receiveAsFlow()

    private val _satelliteState = MutableStateFlow(SatelliteState())
    val satelliteState: StateFlow<SatelliteState> = _satelliteState

    private val nsdHelper = NsdHelper(localFileRepository.context)
    
    val discoveredOrbiters: StateFlow<Set<OrbiterInfo>> = nsdHelper.discoveredServices
        .map { services ->
            services.mapNotNull { service ->
                val hostAddr = service.host?.hostAddress ?: return@mapNotNull null
                val avatarIdBytes = service.attributes?.get("avatarId")
                val avatarId = avatarIdBytes?.let { String(it) } ?: "rocket"
                OrbiterInfo(
                    name = service.serviceName,
                    host = hostAddr,
                    port = service.port,
                    avatarId = avatarId
                )
            }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val activeHost: SavedHost?
        get() = connectionManager.activeHostId?.let { id -> hostRepository.hosts.value.find { it.id == id } }

    val currentPath: String
        get() = _state.value.currentPath

    private fun getServerAddress(): String {
        val config = connectionManager.currentConfig
        return "${config.host}:${config.port}"
    }

    init {
        viewModelScope.launch {
            connectionManager.connectionState.collect { connState ->
                if (connState is ConnectionState.Connected) {
                    refreshCurrentPath()
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.showHiddenFiles.collect { showHidden ->
                _state.update { it.copy(showHiddenFiles = showHidden) }
            }
        }
        viewModelScope.launch {
            NotificationSignals.cancelRequest.collect { path ->
                cancelDownload(path)
            }
        }
        viewModelScope.launch {
            settingsRepository.satelliteEnabled.collect { enabled ->
                _satelliteState.update { it.copy(isRunning = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.satellitePort.collect { port ->
                _satelliteState.update { it.copy(port = port) }
            }
        }
        viewModelScope.launch {
            settingsRepository.satelliteRootUri.collect { uri ->
                _satelliteState.update { it.copy(rootUri = uri) }
            }
        }
        startPingMonitor()
    }

    private fun startPingMonitor() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val currentHosts = hostRepository.hosts.value
                currentHosts.forEach { host ->
                    launch {
                        val ping = tryPing(host.host, host.port)
                        _pingResults.update { it + (host.id to ping) }
                    }
                }
                delay(10_000)
            }
        }
    }

    private suspend fun tryPing(host: String, port: Int): Int? = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 2000)
            socket.close()
            (System.currentTimeMillis() - start).toInt()
        } catch (e: Exception) {
            null
        }
    }

    fun refreshCurrentPath() {
        _state.update { it.copy(isRefreshing = true) }
        loadFiles(_state.value.currentPath, _state.value.showHiddenFiles)
    }

    val isMultiSelect: Boolean
        get() = _state.value.isMultiSelect

    private var _isRefreshing = false

    val showHiddenFiles: Boolean
        get() = _state.value.showHiddenFiles

    val isRefreshing: Boolean
        get() = _isRefreshing

    fun toggleHiddenFiles() {
        val newShowHidden = !settingsRepository.showHiddenFiles.value
        settingsRepository.setShowHiddenFiles(newShowHidden)
        _state.update { it.copy(showHiddenFiles = newShowHidden, isLoading = true) }
        viewModelScope.launch {
            try {
                val client = connectionManager.getClient()
                val files = client.withRetry { client.list(_state.value.currentPath, newShowHidden) }
                val sortedFiles = sortFiles(files, _state.value.sortType, _state.value.sortOrder)
                _state.update {
                    it.copy(
                        files = sortedFiles,
                        isLoading = false,
                        showHiddenFiles = newShowHidden
                    )
                }
                
                val host = activeHost
                val thresholdBytes = (host?.autoLoadLimitKb ?: 512) * 1024L
                
                sortedFiles.filter { !it.isDirectory && it.size <= thresholdBytes }.forEach { f ->
                    launch {
                        try {
                            val cached = fileCache.get(f.path)
                            if (cached == null || cached.isEmpty()) {
                                val data = downloadQuiet(f)
                                fileCache.put(f.path, data)
                                updateDownloadProgress(f, data.size.toLong(), f.size, getServerAddress())
                            }
                        } catch (e: Exception) {
                             if (e !is CancellationException) {
                                updateDownloadState(f, 0, DownloadStatus.FAILED, e.message)
                             }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        showHiddenFiles = newShowHidden,
                        error = e.message ?: "Failed to toggle hidden files"
                    )
                }
            }
        }
    }

    private fun updateDownloadState(file: FileInfo, bytes: Long, status: DownloadStatus, error: String? = null) {
        _downloadStates.update { current ->
            current + (file.path to FileDownloadState(
                path = file.path,
                fileName = file.name,
                bytesDownloaded = bytes,
                totalBytes = file.size,
                status = status,
                serverAddress = getServerAddress(),
                errorMessage = error ?: ""
            ))
        }
    }

    fun updateSort(type: SortType, order: SortOrder) {
        _state.update { 
            val newState = it.copy(sortType = type, sortOrder = order)
            newState.copy(files = sortFiles(newState.files, type, order))
        }
    }

    private fun sortFiles(files: List<FileInfo>, type: SortType, order: SortOrder): List<FileInfo> {
        val comparator = when (type) {
            SortType.Name -> compareBy<FileInfo> { it.name.lowercase() }
            SortType.Date -> compareBy<FileInfo> { it.lastModified }
            SortType.Size -> compareBy<FileInfo> { it.size }
        }
        
        val sorted = if (order == SortOrder.Ascending) {
            files.sortedWith(comparator)
        } else {
            files.sortedWith(comparator.reversed())
        }

        return sorted.sortedByDescending { it.isDirectory }
    }

    fun navigateTo(path: String) {
        if (path == "..") {
            navigateToParent()
        } else {
            loadFiles(path, _state.value.showHiddenFiles)
        }
    }

    private fun navigateToParent() {
        val current = _state.value.currentPath
        val parent = computeParentPath(current)
        if (parent != current) {
            loadFiles(parent, _state.value.showHiddenFiles)
        }
    }

    private fun loadFiles(path: String, showHiddenFiles: Boolean) {
        _state.update { it.copy(isLoading = true, error = null, currentPath = path) }

        viewModelScope.launch {
            try {
                val client = connectionManager.getClient()
                val files = client.withRetry { client.list(path, showHiddenFiles) }
                val sortedFiles = sortFiles(files, _state.value.sortType, _state.value.sortOrder)
                _state.update {
                    it.copy(
                        files = sortedFiles,
                        isLoading = false,
                        isRefreshing = false,
                        currentPath = path,
                        error = null
                    )
                }
                
                val host = activeHost
                val thresholdBytes = (host?.autoLoadLimitKb ?: 512) * 1024L
                
                sortedFiles.filter { !it.isDirectory && it.size <= thresholdBytes }.forEach { f ->
                    launch {
                        try {
                            val cached = fileCache.get(f.path)
                            if (cached == null || cached.isEmpty()) {
                                val data = downloadQuiet(f)
                                fileCache.put(f.path, data)
                                updateDownloadProgress(f, data.size.toLong(), f.size, getServerAddress())
                            }
                        } catch (e: Exception) {
                            if (e !is CancellationException) {
                                updateDownloadState(f, 0, DownloadStatus.FAILED, e.message)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        currentPath = path,
                        error = e.message ?: "Failed to load files"
                    )
                }
            }
        }
    }

    fun openFile(fileInfo: FileInfo) {
        val sizeLimitBytes = 50 * 1024 * 1024L // 50 MB

        if (fileInfo.size >= sizeLimitBytes) {
            _state.update { it.copy(fileToConfirmLargeDownload = fileInfo) }
            return
        }

        val job = viewModelScope.launch {
            try {
                val cached = fileCache.get(fileInfo.path)
                val data = cached ?: downloadAndCache(fileInfo)

                val tmpFile = File(localFileRepository.cacheDir, "orbitfs_files").resolve(fileInfo.name.replace("/", "_").replace(" ", "_"))
                tmpFile.parentFile?.mkdirs()
                tmpFile.writeBytes(data)

                _uiEffects.send(UiEffect.OpenFile(tmpFile, fileInfo.mimeType))
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Timber.e(e, "openFile failed for ${fileInfo.path}")
                    _uiEffects.send(UiEffect.ShowToast("Failed to open: ${e.message}"))
                }
            } finally {
                downloadJobs.remove(fileInfo.path)
            }
        }
        downloadJobs[fileInfo.path] = job
    }

    private fun updateDownloadProgress(file: FileInfo, bytes: Long, total: Long, server: String) {
        _downloadStates.update { current ->
            current + (file.path to FileDownloadState(
                path = file.path,
                fileName = file.name,
                bytesDownloaded = bytes,
                totalBytes = total,
                status = if (bytes >= total && total > 0) DownloadStatus.COMPLETE else DownloadStatus.IN_PROGRESS,
                serverAddress = server
            ))
        }
    }

    fun dismissLargeDownload() {
        _state.update { it.copy(fileToConfirmLargeDownload = null) }
    }

    fun downloadFile(fileInfo: FileInfo) {
        if (fileInfo.isDirectory) {
            viewModelScope.launch { _uiEffects.send(UiEffect.ShowToast("Cannot download directories")) }
            return
        }
        if (settingsRepository.notificationsEnabled.value) {
            localFileRepository.createNotificationChannel(NOTIFICATION_CHANNEL_ID, "Downloads")
        }

        val serverAddr = getServerAddress()
        val notifId = fileInfo.path.hashCode()
        val cancelIntent = localFileRepository.getCancelPendingIntent(fileInfo.path)
        var lastNotifTime = 0L
        
        val job = viewModelScope.launch {
            var downloadUri: Uri? = null
            try {
                val (uri, output) = withContext(Dispatchers.IO) {
                    localFileRepository.getDownloadOutputStream(fileInfo.name)
                }
                downloadUri = uri

                if (uri != null && output != null) {
                    output.use { stream ->
                        val client = connectionManager.getClient()
                        client.streamFile(fileInfo.path, stream) { bytesRead, totalSize ->
                            updateDownloadProgress(fileInfo, bytesRead, totalSize, serverAddr)
                            
                            val now = System.currentTimeMillis()
                            if (settingsRepository.notificationsEnabled.value && now - lastNotifTime > 800) {
                                localFileRepository.showProgressNotification(notifId, NOTIFICATION_CHANNEL_ID, fileInfo.name, bytesRead, totalSize, cancelIntent)
                                lastNotifTime = now
                            }
                        }
                    }
                    localFileRepository.finishDownload(uri)
                    if (settingsRepository.notificationsEnabled.value) {
                        localFileRepository.showCompletedNotification(notifId, NOTIFICATION_CHANNEL_ID, fileInfo.name, uri, fileInfo.mimeType)
                    }
                    _uiEffects.send(UiEffect.ShowToast("Saved ${fileInfo.name} to Downloads"))
                } else {
                    throw IOException("Failed to create download entry")
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Timber.d("downloadFile: Job cancelled for ${fileInfo.path}")
                    withContext(Dispatchers.IO) {
                        downloadUri?.let { localFileRepository.deleteDownload(it) }
                    }
                    localFileRepository.cancelNotification(notifId)
                    updateDownloadState(fileInfo, 0, DownloadStatus.CANCELLED)
                } else {
                    if (settingsRepository.notificationsEnabled.value) {
                        localFileRepository.showErrorNotification(notifId, NOTIFICATION_CHANNEL_ID, fileInfo.name, e.message ?: "Unknown error")
                    }
                    Timber.e(e, "downloadFile failed for ${fileInfo.path}")
                    updateDownloadState(fileInfo, 0, DownloadStatus.FAILED, e.message)
                    _uiEffects.send(UiEffect.ShowToast("Download failed: ${e.message}"))
                }
            } finally {
                downloadJobs.remove(fileInfo.path)
            }
        }
        downloadJobs[fileInfo.path] = job
    }

    suspend fun statFile(path: String): FileInfo {
        val client = connectionManager.getClient()
        return client.stat(path)
    }

    private suspend fun downloadQuiet(fileInfo: FileInfo): ByteArray {
        val client = connectionManager.getClient()
        return client.readFile(fileInfo.path)
    }

    private suspend fun downloadWithProgress(fileInfo: FileInfo, serverAddress: String = ""): ByteArray {
        val client = connectionManager.getClient()
        var lastBytes = 0L
        var lastTime = System.currentTimeMillis()
        val notifId = fileInfo.path.hashCode()
        val cancelIntent = localFileRepository.getCancelPendingIntent(fileInfo.path)
        var lastNotifTime = 0L
        
        return client.withRetry {
            client.readFile(fileInfo.path) { bytesRead, totalSize ->
                val now = System.currentTimeMillis()
                val elapsed = now - lastTime
                val speed = if (elapsed > 0) ((bytesRead - lastBytes) * 1000L / elapsed) else 0L
                lastBytes = bytesRead
                lastTime = now
                _downloadStates.update { current ->
                    current + (fileInfo.path to FileDownloadState(
                        path = fileInfo.path,
                        fileName = fileInfo.name,
                        bytesDownloaded = bytesRead,
                        totalBytes = totalSize,
                        status = if (bytesRead >= totalSize && totalSize > 0) DownloadStatus.COMPLETE else DownloadStatus.IN_PROGRESS,
                        serverAddress = serverAddress,
                        speedBytesPerSecond = speed
                    ))
                }
                if (settingsRepository.notificationsEnabled.value && now - lastNotifTime > 800) {
                    localFileRepository.showProgressNotification(notifId, NOTIFICATION_CHANNEL_ID, fileInfo.name, bytesRead, totalSize, cancelIntent)
                    lastNotifTime = now
                }
            }
        }.also {
            if (settingsRepository.notificationsEnabled.value) {
                localFileRepository.cancelNotification(notifId)
            }
            _downloadStates.update { current ->
                current + (fileInfo.path to FileDownloadState(
                    path = fileInfo.path,
                    fileName = fileInfo.name,
                    bytesDownloaded = it.size.toLong(),
                    totalBytes = it.size.toLong(),
                    status = DownloadStatus.COMPLETE,
                    serverAddress = serverAddress
                ))
            }
        }
    }

     private suspend fun downloadAndCache(fileInfo: FileInfo): ByteArray {
        val data = downloadWithProgress(fileInfo, getServerAddress())
        fileCache.put(fileInfo.path, data)
        return data
    }

    fun shareFile(fileInfo: FileInfo) {
        if (fileInfo.isDirectory) return
        val job = viewModelScope.launch {
            try {
                val cached = fileCache.get(fileInfo.path)
                val data = cached ?: downloadQuiet(fileInfo)
                val tmpFile = File(localFileRepository.cacheDir, "orbitfs_share").resolve(fileInfo.name.replace("/", "_").replace(" ", "_"))
                tmpFile.parentFile?.mkdirs()
                tmpFile.writeBytes(data)
                
                _uiEffects.send(UiEffect.ShareFile(tmpFile, fileInfo.mimeType))
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Timber.e(e, "shareFile failed for ${fileInfo.path}")
                    _uiEffects.send(UiEffect.ShowToast("Share failed: ${e.message}"))
                }
            } finally {
                downloadJobs.remove(fileInfo.path)
            }
        }
        downloadJobs[fileInfo.path] = job
    }

    fun toggleMultiSelect() {
        val current = _state.value
        _state.update {
            if (current.isMultiSelect) {
                it.copy(isMultiSelect = false, selectedPaths = emptySet())
            } else {
                it.copy(isMultiSelect = true)
            }
        }
    }

    fun selectFile(path: String) {
        val current = _state.value
        val newSelected = if (current.selectedPaths.contains(path)) {
            current.selectedPaths - path
        } else {
            current.selectedPaths + path
        }
        _state.update { it.copy(selectedPaths = newSelected) }
    }

    fun clearSelection() {
        _state.update { it.copy(isMultiSelect = false, selectedPaths = emptySet()) }
    }

    fun deselectAll() {
        _state.update { it.copy(selectedPaths = emptySet()) }
    }

    fun selectAll() {
        val current = _state.value
        val newSelected = current.files.map { it.path }.toSet()
        _state.update { it.copy(selectedPaths = newSelected, isMultiSelect = true) }
    }

    fun downloadSelectedFiles() {
        val selected = _state.value.selectedPaths.toList()
        val filesMap = _state.value.files.associateBy { it.path }
        selected.forEach { path ->
            filesMap[path]?.let { fileInfo ->
                if (!fileInfo.isDirectory) {
                    downloadFile(fileInfo)
                }
            }
        }
        clearSelection()
    }

    fun saveSelectedToDownloads() {
        downloadSelectedFiles()
    }

    fun saveToDownloadsWithDestination(uri: Uri, fileInfo: FileInfo) {
        Timber.d("saveToDownloadsWithDestination: uri=$uri, path=${fileInfo.path}")
        if (settingsRepository.notificationsEnabled.value) {
            localFileRepository.createNotificationChannel(NOTIFICATION_CHANNEL_ID, "Downloads")
        }
        val serverAddr = getServerAddress()
        val notifId = fileInfo.path.hashCode()
        val cancelIntent = localFileRepository.getCancelPendingIntent(fileInfo.path)
        var lastNotifTime = 0L
        
        val job = viewModelScope.launch {
            try {
                Timber.d("saveToDownloadsWithDestination: job started for ${fileInfo.path}")
                withContext(Dispatchers.IO) {
                    localFileRepository.openOutputStream(uri)?.use { output ->
                        val client = connectionManager.getClient()
                        Timber.d("saveToDownloadsWithDestination: starting stream for ${fileInfo.path}")
                        client.streamFile(fileInfo.path, output) { bytesRead, totalSize ->
                            updateDownloadProgress(fileInfo, bytesRead, totalSize, serverAddr)
                            
                            val now = System.currentTimeMillis()
                            if (settingsRepository.notificationsEnabled.value && now - lastNotifTime > 800) {
                                localFileRepository.showProgressNotification(notifId, NOTIFICATION_CHANNEL_ID, fileInfo.name, bytesRead, totalSize, cancelIntent)
                                lastNotifTime = now
                            }
                        }
                    }
                }
                Timber.d("saveToDownloadsWithDestination: stream complete for ${fileInfo.path}")
                
                _downloadStates.update { current ->
                    current + (fileInfo.path to FileDownloadState(
                        path = fileInfo.path,
                        fileName = fileInfo.name,
                        bytesDownloaded = fileInfo.size,
                        totalBytes = fileInfo.size,
                        status = DownloadStatus.COMPLETE,
                        serverAddress = serverAddr,
                        savedToPath = uri.toString()
                    ))
                }
                if (settingsRepository.notificationsEnabled.value) {
                    localFileRepository.showCompletedNotification(notifId, NOTIFICATION_CHANNEL_ID, fileInfo.name, uri, fileInfo.mimeType)
                }
                _uiEffects.send(UiEffect.ShowToast("Saved ${fileInfo.name}"))
            } catch (e: Exception) {
                Timber.e(e, "saveToDownloadsWithDestination failed for ${fileInfo.path}")
                if (e is CancellationException) {
                    Timber.d("saveToDownloadsWithDestination: Job cancelled for ${fileInfo.path}")
                    withContext(Dispatchers.IO) {
                        localFileRepository.deleteDownload(uri)
                    }
                    localFileRepository.cancelNotification(notifId)
                    updateDownloadState(fileInfo, 0, DownloadStatus.CANCELLED)
                } else {
                    if (settingsRepository.notificationsEnabled.value) {
                        localFileRepository.showErrorNotification(notifId, NOTIFICATION_CHANNEL_ID, fileInfo.name, e.message ?: "Unknown error")
                    }
                    updateDownloadState(fileInfo, 0, DownloadStatus.FAILED, e.message)
                    _uiEffects.send(UiEffect.ShowToast("Save failed: ${e.message}"))
                }
            } finally {
                downloadJobs.remove(fileInfo.path)
            }
        }
        downloadJobs[fileInfo.path] = job
    }

    fun deleteSelectedFiles() {
        val selected = _state.value.selectedPaths
        selected.forEach { path ->
            fileCache.remove(path)
            _downloadStates.update { it - path }
        }
        viewModelScope.launch {
            try {
                val client = connectionManager.getClient()
                client.withRetry {
                    selected.forEach { client.delete(it) }
                }
                loadFiles(_state.value.currentPath, _state.value.showHiddenFiles)
            } catch (e: Exception) {
                Timber.e(e, "deleteSelectedFiles failed")
                _state.update { it.copy(error = e.message ?: "Delete failed") }
            }
        }
        clearSelection()
    }

    fun deleteFiles(paths: List<String>) {
        paths.forEach { path ->
            fileCache.remove(path)
            _downloadStates.update { it - path }
        }
        viewModelScope.launch {
            try {
                val client = connectionManager.getClient()
                client.withRetry {
                    paths.forEach { client.delete(it) }
                }
                loadFiles(_state.value.currentPath, _state.value.showHiddenFiles)
            } catch (e: Exception) {
                Timber.e(e, "deleteFiles failed")
                _state.update { it.copy(error = e.message ?: "Delete failed") }
            }
        }
        clearSelection()
    }

    fun cancelDownload(path: String) {
        val job = downloadJobs[path]
        if (job != null) {
            Timber.d("cancelDownload: cancelling job for $path")
            job.cancel()
        } else {
            Timber.w("cancelDownload: no active job found for $path, checking state")
            _downloadStates.update { current ->
                val existing = current[path]
                if (existing != null && existing.status == DownloadStatus.IN_PROGRESS) {
                    current + (path to existing.copy(status = DownloadStatus.CANCELLED))
                } else {
                    current
                }
            }
        }
    }

    fun clearTransferHistory() {
        _downloadStates.update { current ->
            current.filter { it.value.status == DownloadStatus.IN_PROGRESS || it.value.status == DownloadStatus.NOT_STARTED }
        }
    }

    fun retryDownload(path: String) {
        viewModelScope.launch {
            try {
                val fileInfo = statFile(path)
                downloadFile(fileInfo)
            } catch (e: Exception) {
                _uiEffects.send(UiEffect.ShowToast("Retry failed: ${e.message}"))
            }
        }
    }

    fun startRadar() {
        nsdHelper.startDiscovery()
    }

    fun stopRadar() {
        nsdHelper.stopDiscovery()
    }

    fun connectToSavedHost(host: SavedHost) {
        connectionManager.connectToSavedHost(host)
    }

    fun connectToConfig(config: ConnectionConfig) {
        connectionManager.connect(config)
    }

    fun disconnect() {
        connectionManager.disconnect()
    }

    fun retryConnection() {
        val config = connectionManager.currentConfig
        connectionManager.connect(config)
    }

    fun addHost(name: String, host: String, port: Int) {
        hostRepository.addHost(SavedHost(
            id = "",
            name = name,
            host = host,
            port = port
        ))
    }

    fun updateHost(id: String, name: String, host: String, port: Int, 
                   guardedDeletion: Boolean, socketTimeoutMs: Int, chunkSizeKb: Int, autoLoadLimitKb: Int) {
        hostRepository.updateHost(SavedHost(
            id = id,
            name = name,
            host = host,
            port = port,
            guardedDeletion = guardedDeletion,
            socketTimeoutMs = socketTimeoutMs,
            chunkSizeKb = chunkSizeKb,
            autoLoadLimitKb = autoLoadLimitKb
        ))
    }
    
    fun updateActiveHostSettings(guardedDeletion: Boolean, socketTimeoutMs: Int, chunkSizeKb: Int, autoLoadLimitKb: Int) {
        activeHost?.let { host ->
            updateHost(host.id, host.name, host.host, host.port, guardedDeletion, socketTimeoutMs, chunkSizeKb, autoLoadLimitKb)
        }
    }

    fun deleteHost(id: String) {
        hostRepository.removeHost(id)
    }

    fun updateSatelliteRoot(uri: Uri, path: String) {
        settingsRepository.setSatelliteRootUri(uri.toString())
        _satelliteState.update { it.copy(rootUri = uri.toString(), rootPath = path) }
    }

    fun updateSatellitePort(port: Int) {
        settingsRepository.setSatellitePort(port)
    }

    fun toggleSatellite(context: Context) {
        val current = _satelliteState.value
        if (current.isRunning) {
            stopSatellite(context)
        } else {
            startSatellite(context)
        }
    }

    private fun startSatellite(context: Context) {
        val rootUri = settingsRepository.satelliteRootUri.value
        if (rootUri == null) {
            viewModelScope.launch { _uiEffects.send(UiEffect.ShowToast("Please select a folder first")) }
            return
        }

        val intent = Intent(context, OrbitFSServerService::class.java).apply {
            action = OrbitFSServerService.ACTION_START
        }
        
        context.startForegroundService(intent)
        settingsRepository.setSatelliteEnabled(true)
        _satelliteState.update { it.copy(pairingToken = "S-${(100..999).random()}") } 
    }

    private fun stopSatellite(context: Context) {
        val intent = Intent(context, OrbitFSServerService::class.java).apply {
            action = OrbitFSServerService.ACTION_STOP
        }
        context.startService(intent)
        settingsRepository.setSatelliteEnabled(false)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "orbitfs_downloads"
    }
}

fun computeParentPath(current: String): String {
    if (current.isEmpty() || current == "/") return ""
    val trimmed = current.removeSuffix("/")
    val lastSlash = trimmed.lastIndexOf("/")
    return if (lastSlash <= 0) "" else trimmed.substring(0, lastSlash)
}
