package org.orbitfs.common.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.orbitfs.common.client.ConnectionManager
import org.orbitfs.common.client.ConnectionState
import org.orbitfs.common.client.OrbitRadar
import org.orbitfs.common.data.ConnectionConfig
import org.orbitfs.common.data.HostRepository
import org.orbitfs.common.data.LocalFileRepository
import org.orbitfs.common.data.SavedHost
import org.orbitfs.common.data.SettingsRepository
import org.orbitfs.common.model.*
import org.orbitfs.common.util.OrbitLogger
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class FileBrowserViewModel(
    private val connectionManager: ConnectionManager,
    private val hostRepository: HostRepository,
    private val settingsRepository: SettingsRepository,
    private val localFileRepository: LocalFileRepository,
    private val orbitRadar: OrbitRadar,
    private val viewModelScope: CoroutineScope,
    private val onToggleSatellite: () -> Unit = {}
) {
    private val TAG = "FileBrowserVM"

    private val downloadJobs = mutableMapOf<String, Job>()
    // Simple cache using LinkedHashMap
    private val fileCache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean {
            return size > 20 // Keep last 20 files
        }
    }

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

    val discoveredOrbiters: StateFlow<Set<OrbiterInfo>> = orbitRadar.discoveredOrbiters

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    val activeHost: SavedHost?
        get() = connectionManager.activeHostId?.let { id -> hostRepository.hosts.value.find { it.id == id } }

    val currentPath: String
        get() = _state.value.currentPath

    init {
        viewModelScope.launch {
            connectionManager.connectionState.collect { connState ->
                if (connState is ConnectionState.Connected) {
                    OrbitLogger.d(TAG, "Connected to ${connState.config.name}, loading root...")
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
            settingsRepository.satelliteEnabled.collect { enabled ->
                OrbitLogger.d(TAG, "Satellite enabled state changed: $enabled")
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
                _satelliteState.update { it.copy(rootPath = uri) }
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
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
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
        val sorted = if (order == SortOrder.Ascending) files.sortedWith(comparator) else files.sortedWith(comparator.reversed())
        return sorted.sortedByDescending { it.isDirectory }
    }

    fun navigateTo(path: String) {
        OrbitLogger.d(TAG, "Navigating to: $path")
        val target = if (path == "..") {
            val current = _state.value.currentPath
            val parts = current.split("/").filter { it.isNotEmpty() }
            if (parts.size <= 1) "" else "/" + parts.dropLast(1).joinToString("/")
        } else {
            val parts = path.split("/").filter { it.isNotEmpty() }
            if (parts.isEmpty()) "" else "/" + parts.joinToString("/")
        }
        loadFiles(target, _state.value.showHiddenFiles)
    }

    private fun loadFiles(path: String, showHiddenFiles: Boolean) {
        OrbitLogger.d(TAG, "Loading files for path: $path")
        _state.update { it.copy(isLoading = true, error = null, currentPath = path) }
        viewModelScope.launch {
            try {
                val client = connectionManager.getClient()
                val files = client.withRetry { client.list(path, showHiddenFiles) }
                OrbitLogger.d(TAG, "Found ${files.size} entries")
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
            } catch (e: Exception) {
                OrbitLogger.e(TAG, "Failed to load files", e)
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun openFile(fileInfo: FileInfo) {
        viewModelScope.launch {
            try {
                val data = downloadQuiet(fileInfo)
                val tmpFile = File(localFileRepository.getCacheDir(), fileInfo.name)
                tmpFile.writeBytes(data)
                _uiEffects.send(UiEffect.OpenFile(tmpFile, fileInfo.mimeType))
            } catch (e: Exception) {
                _uiEffects.send(UiEffect.ShowToast("Failed to open: ${e.message}"))
            }
        }
    }

    fun downloadFile(fileInfo: FileInfo) {
        viewModelScope.launch {
            try {
                val (path, output) = localFileRepository.getDownloadOutputStream(fileInfo.name)
                if (output != null) {
                    output.use { stream ->
                        val client = connectionManager.getClient()
                        client.streamFile(fileInfo.path, stream) { bytesRead, totalSize ->
                            updateDownloadProgress(fileInfo, bytesRead, totalSize)
                        }
                    }
                    localFileRepository.finishDownload(fileInfo.name)
                    _uiEffects.send(UiEffect.ShowToast("Downloaded ${fileInfo.name}"))
                }
            } catch (e: Exception) {
                _uiEffects.send(UiEffect.ShowToast("Download failed: ${e.message}"))
            }
        }
    }

    private fun updateDownloadProgress(file: FileInfo, bytes: Long, total: Long) {
        _downloadStates.update { current ->
            current + (file.path to FileDownloadState(
                path = file.path,
                fileName = file.name,
                bytesDownloaded = bytes,
                totalBytes = total,
                status = if (bytes >= total && total > 0) DownloadStatus.COMPLETE else DownloadStatus.IN_PROGRESS
            ))
        }
    }

    private suspend fun downloadQuiet(fileInfo: FileInfo): ByteArray {
        val client = connectionManager.getClient()
        return client.readFile(fileInfo.path)
    }

    fun shareFile(fileInfo: FileInfo) {
        viewModelScope.launch {
            try {
                val data = downloadQuiet(fileInfo)
                val tmpFile = File(localFileRepository.getCacheDir(), "share_" + fileInfo.name)
                tmpFile.writeBytes(data)
                _uiEffects.send(UiEffect.ShareFile(tmpFile, fileInfo.mimeType))
            } catch (e: Exception) {
                _uiEffects.send(UiEffect.ShowToast("Share failed: ${e.message}"))
            }
        }
    }

    fun toggleMultiSelect() {
        _state.update { it.copy(isMultiSelect = !it.isMultiSelect, selectedPaths = emptySet()) }
    }

    fun selectFile(path: String) {
        _state.update {
            val newSelected = if (it.selectedPaths.contains(path)) it.selectedPaths - path else it.selectedPaths + path
            it.copy(selectedPaths = newSelected)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(isMultiSelect = false, selectedPaths = emptySet()) }
    }

    fun deleteSelectedFiles() {
        val selected = _state.value.selectedPaths
        viewModelScope.launch {
            try {
                val client = connectionManager.getClient()
                selected.forEach { client.delete(it) }
                refreshCurrentPath()
            } catch (e: Exception) {
                _uiEffects.send(UiEffect.ShowToast("Delete failed: ${e.message}"))
            }
        }
        clearSelection()
    }

    fun cancelDownload(path: String) {
        downloadJobs[path]?.cancel()
        downloadJobs.remove(path)
    }

    fun clearTransferHistory() {
        _downloadStates.update { current ->
            current.filter { it.value.status == DownloadStatus.IN_PROGRESS }
        }
    }

    fun retryDownload(path: String) {
        val state = _downloadStates.value[path] ?: return
        viewModelScope.launch {
            try {
                val outputFile = localFileRepository.getDownloadOutputStream(state.fileName)
                val outputStream = outputFile.second ?: throw IllegalStateException("No output stream available")
                outputStream.use { stream ->
                    val client = connectionManager.getClient()
                    client.streamFile(state.path, stream) { bytesRead, totalSize ->
                        updateDownloadProgress(
                            FileInfo(
                                name = state.fileName,
                                path = state.path,
                                size = totalSize,
                                isDirectory = false
                            ),
                            bytesRead,
                            totalSize
                        )
                    }
                }
                localFileRepository.finishDownload(state.fileName)
                _uiEffects.send(UiEffect.ShowToast("Retry download complete: ${state.fileName}"))
            } catch (e: Exception) {
                _uiEffects.send(UiEffect.ShowToast("Retry failed: ${e.message}"))
            }
        }
    }

    fun startRadar() {
        orbitRadar.startDiscovery(settingsRepository.nodeId.value)
    }
    fun stopRadar() { orbitRadar.stopDiscovery() }

    fun connectToSavedHost(host: SavedHost) { connectionManager.connectToSavedHost(host) }
    fun disconnect() { connectionManager.disconnect() }

    fun addHost(name: String, host: String, port: Int) {
        hostRepository.addHost(SavedHost(id = "", name = name, host = host, port = port))
    }

    fun updateHost(host: SavedHost) { hostRepository.updateHost(host) }
    fun deleteHost(id: String) { hostRepository.removeHost(id) }

    fun updateActiveHostSettings(guarded: Boolean, timeout: Int, chunk: Int, autoload: Int) {
        activeHost?.let {
            updateHost(it.copy(guardedDeletion = guarded, socketTimeoutMs = timeout, chunkSizeKb = chunk, autoLoadLimitKb = autoload))
        }
    }

    fun updateSatelliteRoot(uri: String, path: String) {
        settingsRepository.setSatelliteRootUri(uri)
        _satelliteState.update { it.copy(rootUri = uri, rootPath = path) }
    }

    fun updateSatellitePort(port: Int) { settingsRepository.setSatellitePort(port) }

    fun toggleSatellite() {
        OrbitLogger.d(TAG, "Toggling satellite...")
        onToggleSatellite()
    }

    fun dismissLargeDownload() { _state.update { it.copy(fileToConfirmLargeDownload = null) } }
}
