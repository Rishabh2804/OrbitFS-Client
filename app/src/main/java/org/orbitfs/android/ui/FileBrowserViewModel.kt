package org.orbitfs.android.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.collection.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.orbitfs.android.R
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.client.ConnectionState
import org.orbitfs.android.data.ConnectionConfig
import org.orbitfs.android.data.HostRepository
import org.orbitfs.android.data.SavedHost
import org.orbitfs.android.data.SettingsRepository
import org.orbitfs.android.model.BrowserState
import org.orbitfs.android.model.DownloadStatus
import org.orbitfs.android.model.FileDownloadState
import org.orbitfs.android.model.FileInfo
import org.orbitfs.android.util.MimeTypeUtil
import timber.log.Timber
import java.io.File
import java.io.IOException

class FileBrowserViewModel(
    private val connectionManager: ConnectionManager,
    private val hostRepository: HostRepository,
    private val settingsRepository: SettingsRepository,
    private val context: Context
) : ViewModel() {

    private val fileCache = object : LruCache<String, ByteArray>(50 * 1024 * 1024) {}

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state

    private val _downloadStates = MutableStateFlow<Map<String, FileDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, FileDownloadState>> = _downloadStates

    val currentPath: String
        get() = _state.value.currentPath

    private val notifManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
    }

    fun refreshCurrentPath() {
        loadFiles(_state.value.currentPath, _state.value.showHiddenFiles)
    }

    val isMultiSelect: Boolean
        get() = _state.value.isMultiSelect

    val showHiddenFiles: Boolean
        get() = _state.value.showHiddenFiles

    fun toggleHiddenFiles() {
        val newShowHidden = !settingsRepository.showHiddenFiles.value
        settingsRepository.setShowHiddenFiles(newShowHidden)
        loadFiles(_state.value.currentPath, newShowHidden)
    }

    fun showSettings() {
        // Settings are handled through SettingsRepository flows
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
                _state.update {
                    it.copy(
                        files = files,
                        isLoading = false,
                        currentPath = path,
                        error = null
                    )
                }
                val thresholdBytes = settingsRepository.autoLoadThresholdKb.value * 1024L
                files.filter { !it.isDirectory && it.size <= thresholdBytes }.forEach { f ->
                    viewModelScope.launch {
                        try {
                            val cached = fileCache.get(f.path)
                            if (cached == null || cached.isEmpty()) {
                                val data = downloadQuiet(f)
                                fileCache.put(f.path, data)
                                _downloadStates.update { current ->
                                    current + (f.path to FileDownloadState(
                                        path = f.path,
                                        fileName = f.name,
                                        bytesDownloaded = data.size.toLong(),
                                        totalBytes = f.size,
                                        status = DownloadStatus.COMPLETE
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            _downloadStates.update { current ->
                                current + (f.path to FileDownloadState(
                                    path = f.path,
                                    fileName = f.name,
                                    bytesDownloaded = 0L,
                                    totalBytes = f.size,
                                    status = DownloadStatus.FAILED
                                ))
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        currentPath = path,
                        error = e.message ?: "Failed to load files"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        currentPath = path,
                        error = e.message ?: "Unexpected error"
                    )
                }
            }
        }
    }

    fun openFile(fileInfo: FileInfo, ctx: Context) {
        val autoLoadThreshold = settingsRepository.autoLoadThresholdKb.value * 1024L

        viewModelScope.launch {
            try {
                val cached = fileCache.get(fileInfo.path)
                val data = cached ?: downloadAndCache(fileInfo)

                val tmpFile = File(ctx.cacheDir, "orbitfs_files").resolve(fileInfo.name.replace("/", "_").replace(" ", "_"))
                tmpFile.parentFile?.mkdirs()
                tmpFile.writeBytes(data)

                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        ctx,
                        "${ctx.packageName}.fileprovider",
                        tmpFile
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, MimeTypeUtil.getMimeType(ctx, tmpFile, cached))
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    if (intent.resolveActivity(ctx.packageManager) != null) {
                        ctx.startActivity(intent)
                    } else {
                        if (fileInfo.size > autoLoadThreshold) {
                            downloadFile(fileInfo, ctx)
                        } else {
                            Toast.makeText(ctx, "No app to open ${fileInfo.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "openFile failed for ${fileInfo.path}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Failed to open: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun downloadFile(fileInfo: FileInfo, ctx: Context) {
        if (fileInfo.isDirectory) {
            Toast.makeText(ctx, "Cannot download directories", Toast.LENGTH_SHORT).show()
            return
        }
        createNotificationChannel()

        viewModelScope.launch {
            try {
                val data = downloadWithProgress(fileInfo)
                fileCache.put(fileInfo.path, data)

                saveToDownloads(ctx, fileInfo.name, data)

                updateNotificationComplete(fileInfo.name)

                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Saved ${fileInfo.name} to Downloads", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                failNotification(fileInfo.name, e.message ?: "Download failed")
                Timber.e(e, "downloadFile failed for ${fileInfo.path}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun saveToDownloads(ctx: Context, name: String, data: ByteArray) {
        withContext(Dispatchers.IO) {
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, MimeTypeUtil.getMimeType(ctx, File("/tmp/$name"), data))
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ctx.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )
            } else {
                @Suppress("DEPRECATION")
                ctx.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"),
                    contentValues.apply {
                        put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Download")
                    }
                )
            }

            if (uri != null) {
                try {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    ctx.contentResolver.update(uri, contentValues, null, null)
                } catch (e: IOException) {
                    ctx.contentResolver.delete(uri, null, null)
                    throw e
                }
            } else {
                throw IOException("Failed to create download entry")
            }
        }
    }

    private suspend fun downloadQuiet(fileInfo: FileInfo): ByteArray {
        val client = connectionManager.getClient()
        return client.readFile(fileInfo.path)
    }

    private suspend fun downloadWithProgress(fileInfo: FileInfo): ByteArray {
        Timber.d("downloadWithProgress: starting for ${fileInfo.path} (size=${fileInfo.size})")
        val client = connectionManager.getClient()
        return client.withRetry {
            client.readFile(fileInfo.path) { bytesRead, totalSize ->
                val progress = if (totalSize > 0) bytesRead.toFloat() / totalSize else 0f
                _downloadStates.update { current ->
                    current + (fileInfo.path to FileDownloadState(
                        path = fileInfo.path,
                        fileName = fileInfo.name,
                        bytesDownloaded = bytesRead,
                        totalBytes = totalSize,
                        status = if (bytesRead >= totalSize) DownloadStatus.COMPLETE else DownloadStatus.IN_PROGRESS
                    ))
                }
                updateNotification(fileInfo.name, bytesRead, totalSize)
            }
        }.also {
            Timber.d("downloadWithProgress: completed for ${fileInfo.path} (${it.size} bytes)")
            _downloadStates.update { current ->
                current + (fileInfo.path to FileDownloadState(
                    path = fileInfo.path,
                    fileName = fileInfo.name,
                    bytesDownloaded = it.size.toLong(),
                    totalBytes = it.size.toLong(),
                    status = DownloadStatus.COMPLETE
                ))
            }
        }
    }

    private suspend fun downloadAndCache(fileInfo: FileInfo): ByteArray {
        val data = downloadWithProgress(fileInfo)
        fileCache.put(fileInfo.path, data)
        return data
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

    fun downloadSelectedFiles(ctx: Context) {
        val selected = _state.value.selectedPaths.toList()
        val filesMap = _state.value.files.associateBy { it.path }
        selected.forEach { path ->
            filesMap[path]?.let { fileInfo ->
                if (!fileInfo.isDirectory) {
                    downloadFile(fileInfo, ctx)
                }
            }
        }
        clearSelection()
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

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notifManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(fileName: String, downloaded: Long, total: Long) {
        val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
        notifManager.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloading $fileName")
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build())
    }

    private fun updateNotificationComplete(fileName: String) {
        notifManager.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Download complete")
            .setSmallIcon(R.drawable.ic_download)
            .setAutoCancel(true)
            .setProgress(100, 100, false)
            .build())
    }

    private fun failNotification(fileName: String, error: String) {
        notifManager.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Download failed")
            .setSmallIcon(R.drawable.ic_download)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .build())
    }

    fun cancelDownload(path: String) {
        _downloadStates.update { current ->
            current - path
        }
        fileCache.remove(path)
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
            name = name.ifEmpty { host },
            host = host,
            port = port
        ))
    }

    fun updateHost(id: String, name: String, host: String, port: Int) {
        hostRepository.updateHost(SavedHost(
            id = id,
            name = name.ifEmpty { host },
            host = host,
            port = port
        ))
    }

    fun deleteHost(id: String) {
        hostRepository.removeHost(id)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "orbitfs_downloads"
    }
}

fun computeParentPath(current: String): String {
    if (current.isEmpty() || current == "/") return ""
    val trimmed = current.removeSuffix("/")
    val lastSlash = trimmed.lastIndexOf("/")
    return if (lastSlash <= 0) "" else trimmed.substring(0, lastSlash)
}
