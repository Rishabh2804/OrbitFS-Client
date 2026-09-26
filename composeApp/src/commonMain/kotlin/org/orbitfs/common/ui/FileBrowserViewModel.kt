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
import org.orbitfs.common.util.MimeTypeUtil
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

    init {
        viewModelScope.launch {
            connectionManager.connectionState.collect { connState ->
                when (connState) {
                    is ConnectionState.Connected -> {
                        _state.update { it.copy(currentPath = "/", files = emptyList(), error = null) }
                        refreshCurrentPath()
                    }
                    is ConnectionState.Disconnected -> {
                        _state.update { it.copy(currentPath = "", files = emptyList()) }
                    }
                    else -> {}
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
                _satelliteState.update { it.copy(isRunning = enabled) }
            }
        }
        
        viewModelScope.launch {
            combine(
                settingsRepository.satellitePort, 
                settingsRepository.satelliteRootUri, 
                settingsRepository.satelliteRootName
            ) { port, uri, name ->
                Triple(port, uri, name)
            }.collect { (port, uri, name) ->
                val display = when {
                    uri == "/Users" -> "Mac Users"
                    uri != null && (uri.contains("/storage/emulated/0") || uri.endsWith("/Test")) -> name ?: "Test"
                    !name.isNullOrBlank() -> name
                    !uri.isNullOrBlank() -> uri
                    else -> "Internal Storage (Default)"
                }
                
                _satelliteState.update { it.copy(
                    port = port, 
                    rootUri = uri, 
                    rootPath = display
                ) }
            }
        }
        
        viewModelScope.launch {
            orbitRadar.discoveredOrbiters.collect { orbiters ->
                val saved = hostRepository.hosts.value
                orbiters.forEach { orbiter ->
                    val existing = saved.find { it.nodeId == orbiter.nodeId && it.nodeId.isNotEmpty() }
                    if (existing != null && (existing.name != orbiter.name || existing.lastSeenAvatarId != orbiter.avatarId)) {
                        hostRepository.updateHost(existing.copy(
                            name = orbiter.name,
                            lastSeenAvatarId = orbiter.avatarId
                        ))
                    }
                }
            }
        }

        startPingMonitor()
    }

    private fun startPingMonitor() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val currentHosts = hostRepository.hosts.value
                currentHosts.forEach { host ->
                    launch {
                        val ping = tryPing(host.host, host.port)
                        _pingResults.update { it + (host.id to ping) }
                    }
                }
                delay(4000)
            }
        }
    }

    private suspend fun tryPing(host: String, port: Int): Int? = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 1500)
            socket.close()
            10
        } catch (e: Exception) { null }
    }

    fun refreshCurrentPath() {
        _state.update { it.copy(isRefreshing = true) }
        loadFiles(_state.value.currentPath, _state.value.showHiddenFiles)
    }

    fun toggleHiddenFiles() {
        val next = !settingsRepository.showHiddenFiles.value
        settingsRepository.setShowHiddenFiles(next)
    }

    fun updateSort(type: SortType, order: SortOrder) {
        _state.update { it.copy(sortType = type, sortOrder = order) }
        _state.update { it.copy(files = sortFiles(it.files, type, order)) }
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
        val target = if (path == "..") {
            val current = _state.value.currentPath.trim('/')
            if (current.isEmpty()) "/"
            else {
                val parent = current.substringBeforeLast("/", "").ifEmpty { "" }
                "/$parent"
            }
        } else {
            if (path.startsWith("/")) path else "/$path"
        }
        loadFiles(target.replace("//", "/"), _state.value.showHiddenFiles)
    }

    private fun loadFiles(path: String, showHidden: Boolean) {
        _state.update { it.copy(isLoading = true, error = null, currentPath = path) }
        viewModelScope.launch {
            try {
                val client = connectionManager.getClient()
                val files = client.withRetry { client.list(path, showHidden) }
                val sorted = sortFiles(files, _state.value.sortType, _state.value.sortOrder)
                _state.update { it.copy(files = sorted, isLoading = false, isRefreshing = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Failed to load files") }
            }
        }
    }

    /**
     * Fixes File Opening: Uses streaming for reliability and memory efficiency.
     * Implements 50MB warning threshold.
     */
    fun openFile(file: FileInfo) {
        if (file.size > 50 * 1024 * 1024) { // 50MB
            _state.update { it.copy(fileToConfirmLargeDownload = file) }
            return
        }
        
        viewModelScope.launch {
            _state.update { it.copy(loadingItemPaths = it.loadingItemPaths + file.path) }
            try {
                val tmp = File(localFileRepository.getCacheDir(), file.name)
                tmp.outputStream().use { output ->
                    val client = connectionManager.getClient()
                    client.streamFile(file.path, output) { _, _ -> }
                }
                
                // Read a small chunk for Mime detection if needed
                val dataPreview = if (tmp.exists() && tmp.length() > 0) {
                    tmp.inputStream().use { input ->
                        val buf = ByteArray(512)
                        val read = input.read(buf)
                        if (read > 0) buf.copyOf(read) else byteArrayOf()
                    }
                } else byteArrayOf()
                
                val detectedMime = MimeTypeUtil.getMimeType(tmp, dataPreview)
                _uiEffects.send(UiEffect.OpenFile(tmp, detectedMime))
            } catch (e: Exception) {
                _uiEffects.send(UiEffect.ShowToast("Open failed: ${e.message}"))
            } finally {
                _state.update { it.copy(loadingItemPaths = it.loadingItemPaths - file.path) }
            }
        }
    }

    /**
     * Opens a file that was already downloaded (from history).
     */
    fun openLocalFile(state: FileDownloadState) {
        val targetPath = state.savedToPath.ifEmpty { state.path }
        if (targetPath.startsWith("content://")) {
            val mime = MimeTypeUtil.getMimeType(File(state.fileName))
            viewModelScope.launch {
                _uiEffects.send(UiEffect.OpenFile(File(targetPath), mime))
            }
        } else {
            val file = File(targetPath)
            if (file.exists()) {
                val mime = MimeTypeUtil.getMimeType(file)
                viewModelScope.launch {
                    _uiEffects.send(UiEffect.OpenFile(file, mime))
                }
            } else {
                viewModelScope.launch {
                    _uiEffects.send(UiEffect.ShowToast("Local file not found"))
                }
            }
        }
    }

    fun downloadFile(file: FileInfo, targetDirUri: String? = null) {
        val job = viewModelScope.launch {
            var resolvedLocalPath = ""
            try {
                _uiEffects.send(UiEffect.ShowToast("Download started: ${file.name}"))
                val (localPath, out) = localFileRepository.getDownloadOutputStream(file.name, targetDirUri)
                resolvedLocalPath = localPath ?: ""
                updateProgress(file, 0, file.size, resolvedLocalPath)
                if (out != null) {
                    out.use { stream ->
                        val client = connectionManager.getClient()
                        client.streamFile(file.path, stream) { p, t -> updateProgress(file, p, t, resolvedLocalPath) }
                    }
                    localFileRepository.finishDownload(file.name)
                    _uiEffects.send(UiEffect.ShowToast("Downloaded ${file.name}"))
                } else {
                    val msg = "Could not open destination storage location"
                    updateProgress(file, 0, file.size, resolvedLocalPath, isFailed = true, errorMsg = msg)
                    _uiEffects.send(UiEffect.ShowToast("Download failed: $msg"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    _downloadStates.update { map ->
                        val existing = map[file.path]
                        if (existing != null) map + (file.path to existing.copy(status = DownloadStatus.CANCELLED, speedBytesPerSecond = 0L))
                        else map
                    }
                    _uiEffects.send(UiEffect.ShowToast("Download cancelled: ${file.name}"))
                } else {
                    val msg = e.message ?: "Unknown transfer error"
                    OrbitLogger.e(TAG, "Download failed for ${file.name}", e)
                    updateProgress(file, 0, file.size, resolvedLocalPath, isFailed = true, errorMsg = msg)
                    _uiEffects.send(UiEffect.ShowToast("Download failed: $msg"))
                }
            } finally {
                downloadJobs.remove(file.path)
            }
        }
        downloadJobs[file.path] = job
    }

    private fun updateProgress(file: FileInfo, p: Long, t: Long, savedToPath: String = "", isFailed: Boolean = false, errorMsg: String = "") {
        val now = System.currentTimeMillis()
        _downloadStates.update { currentMap ->
            val existing = currentMap[file.path]
            val localPath = if (savedToPath.isNotEmpty()) savedToPath else (existing?.savedToPath ?: "")
            
            val lastTime = existing?.lastUpdated ?: now
            val lastBytes = existing?.bytesDownloaded ?: 0L
            val timeDeltaMs = now - lastTime
            val bytesDelta = p - lastBytes
            
            val speed = if (timeDeltaMs >= 250 && bytesDelta >= 0) {
                (bytesDelta * 1000) / timeDeltaMs
            } else if (p >= t && t > 0) {
                0L
            } else {
                existing?.speedBytesPerSecond ?: 0L
            }

            val status = when {
                isFailed -> DownloadStatus.FAILED
                p >= t && t > 0 -> DownloadStatus.COMPLETE
                else -> DownloadStatus.IN_PROGRESS
            }

            currentMap + (file.path to FileDownloadState(
                path = file.path,
                fileName = file.name,
                bytesDownloaded = p,
                totalBytes = t,
                status = status,
                savedToPath = localPath,
                errorMessage = errorMsg,
                speedBytesPerSecond = if (status == DownloadStatus.COMPLETE || isFailed) 0L else speed,
                lastUpdated = now
            ))
        }
    }

    fun shareFile(file: FileInfo) {
        viewModelScope.launch {
            _state.update { it.copy(loadingItemPaths = it.loadingItemPaths + file.path) }
            try {
                val tmp = File(localFileRepository.getCacheDir(), "share_" + file.name)
                tmp.outputStream().use { output ->
                    val client = connectionManager.getClient()
                    client.streamFile(file.path, output) { _, _ -> }
                }
                _uiEffects.send(UiEffect.ShareFile(tmp, file.mimeType))
            } catch (e: Exception) {
                _uiEffects.send(UiEffect.ShowToast("Share failed"))
            } finally {
                _state.update { it.copy(loadingItemPaths = it.loadingItemPaths - file.path) }
            }
        }
    }

    /**
     * Fixes Multi-Select Bug: Toggle selection state.
     */
    fun toggleMultiSelect() { 
        _state.update { 
            val next = !it.isMultiSelect
            it.copy(
                isMultiSelect = next,
                selectedPaths = if (next) it.selectedPaths else emptySet()
            )
        }
    }
    
    fun selectFile(path: String) { 
        _state.update { 
            val current = it.selectedPaths
            it.copy(selectedPaths = if (current.contains(path)) current - path else current + path) 
        } 
    }
    
    fun clearSelection() { _state.update { it.copy(isMultiSelect = false, selectedPaths = emptySet()) } }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            try {
                val client = connectionManager.getClient()
                val pathsToDelete = _state.value.selectedPaths.toList()
                pathsToDelete.forEach { client.withRetry { client.delete(it) } }
                refreshCurrentPath()
                clearSelection()
            } catch (e: Exception) { _uiEffects.send(UiEffect.ShowToast("Delete failed")) }
        }
    }

    fun cancelDownload(path: String) {
        downloadJobs[path]?.cancel()
        downloadJobs.remove(path)
    }

    fun clearTransferHistory() { _downloadStates.update { m -> m.filter { it.value.status == DownloadStatus.IN_PROGRESS } } }

    fun deleteTransferItem(path: String, deleteFileFromDisk: Boolean = false) {
        val st = _downloadStates.value[path]
        if (st != null && deleteFileFromDisk && st.savedToPath.isNotEmpty() && !st.savedToPath.startsWith("content://")) {
            try {
                val f = File(st.savedToPath)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }
        _downloadStates.update { currentMap -> currentMap - path }
    }

    fun retryDownload(path: String) {
        val st = _downloadStates.value[path] ?: return
        downloadFile(FileInfo(name = st.fileName, path = st.path, size = st.totalBytes, isDirectory = false))
    }

    fun startRadar() { orbitRadar.startDiscovery(settingsRepository.nodeId.value) }
    fun stopRadar() { orbitRadar.stopDiscovery() }

    fun connectToSavedHost(host: SavedHost) { connectionManager.connectToSavedHost(host) }
    fun disconnect() { connectionManager.disconnect() }

    fun addHost(name: String, host: String, port: Int, nodeId: String) {
        val saved = hostRepository.hosts.value
        val existing = saved.find { it.nodeId == nodeId && nodeId.isNotEmpty() }
        if (existing != null) {
            hostRepository.updateHost(existing.copy(name = name, host = host, port = port))
        } else {
            val id = (System.currentTimeMillis() + (0..1000).random()).toString()
            hostRepository.addHost(SavedHost(id = id, nodeId = nodeId, name = name, host = host, port = port))
        }
    }

    fun updateHost(host: SavedHost) { hostRepository.updateHost(host) }
    fun deleteHost(id: String) { hostRepository.removeHost(id) }

    fun updateActiveHostSettings(g: Boolean, t: Int, c: Int, a: Int) {
        activeHost?.let { updateHost(it.copy(guardedDeletion = g, socketTimeoutMs = t, chunkSizeKb = c, autoLoadLimitKb = a)) }
    }

    /**
     * Fixes Configuration Restart: Explicitly toggles and waits for the repo state change.
     */
    fun updateSatelliteConfig(port: Int, rootUri: String?) {
        val wasRunning = settingsRepository.satelliteEnabled.value
        viewModelScope.launch {
            if (wasRunning) {
                onToggleSatellite() // STOP
                // Wait for repository flow to reflect STOP before continuing
                settingsRepository.satelliteEnabled.filter { !it }.first()
                delay(1200) // Increased delay for physical device unbinding
            }
            
            settingsRepository.setSatellitePort(port)
            if (rootUri != null) {
                val name = try { File(rootUri).name } catch (_: Exception) { null }
                settingsRepository.setSatelliteRootUri(rootUri, name)
            }
            
            delay(500)
            
            // Start it
            onToggleSatellite() 
        }
    }

    fun updateSatelliteRoot(u: String, p: String) {
        settingsRepository.setSatelliteRootUri(u, p)
    }

    fun updateSatellitePort(p: Int) { settingsRepository.setSatellitePort(p) }
    fun toggleSatellite() { onToggleSatellite() }
    fun dismissLargeDownload() { _state.update { it.copy(fileToConfirmLargeDownload = null) } }
    
    fun resetIdentity() {
        settingsRepository.resetIdentity()
    }
}
