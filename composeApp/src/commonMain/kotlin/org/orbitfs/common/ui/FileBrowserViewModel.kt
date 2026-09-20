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
            }
        }
    }

    /**
     * Opens a file that was already downloaded (from history).
     */
    fun openLocalFile(state: FileDownloadState) {
        val file = File(state.path) // Path in state is local path after download
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

    fun downloadFile(file: FileInfo, targetDirUri: String? = null) {
        viewModelScope.launch {
            try {
                // localFileRepository implementation should handle the Uri if provided
                val (path, out) = localFileRepository.getDownloadOutputStream(file.name, targetDirUri)
                if (out != null) {
                    out.use { stream ->
                        val client = connectionManager.getClient()
                        client.streamFile(file.path, stream) { p, t -> updateProgress(file, p, t) }
                    }
                    localFileRepository.finishDownload(file.name)
                    _uiEffects.send(UiEffect.ShowToast("Downloaded ${file.name}"))
                }
            } catch (e: Exception) {
                _uiEffects.send(UiEffect.ShowToast("Download failed: ${e.message}"))
            }
        }
    }

    private fun updateProgress(file: FileInfo, p: Long, t: Long) {
        _downloadStates.update { it + (file.path to FileDownloadState(file.path, file.name, p, t, if (p >= t && t > 0) DownloadStatus.COMPLETE else DownloadStatus.IN_PROGRESS)) }
    }

    fun shareFile(file: FileInfo) {
        viewModelScope.launch {
            try {
                val tmp = File(localFileRepository.getCacheDir(), "share_" + file.name)
                tmp.outputStream().use { output ->
                    val client = connectionManager.getClient()
                    client.streamFile(file.path, output) { _, _ -> }
                }
                _uiEffects.send(UiEffect.ShareFile(tmp, file.mimeType))
            } catch (e: Exception) {
                _uiEffects.send(UiEffect.ShowToast("Share failed"))
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

    fun cancelDownload(path: String) { downloadJobs[path]?.cancel() }
    fun clearTransferHistory() { _downloadStates.update { m -> m.filter { it.value.status == DownloadStatus.IN_PROGRESS } } }

    fun retryDownload(path: String) {
        val st = _downloadStates.value[path] ?: return
        viewModelScope.launch {
            try {
                val (outputPath, out) = localFileRepository.getDownloadOutputStream(st.fileName)
                if (out != null) {
                    out.use { stream ->
                        val client = connectionManager.getClient()
                        client.streamFile(st.path, stream) { p, t -> updateProgress(FileInfo(st.fileName, st.path, t, false), p, t) }
                    }
                    localFileRepository.finishDownload(st.fileName)
                    _uiEffects.send(UiEffect.ShowToast("Retry complete"))
                }
            } catch (e: Exception) {
                _uiEffects.send(UiEffect.ShowToast("Retry failed"))
            }
        }
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
