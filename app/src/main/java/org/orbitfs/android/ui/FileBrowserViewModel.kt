package org.orbitfs.android.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.client.ConnectionState
import org.orbitfs.android.data.ConnectionConfig
import org.orbitfs.android.data.HostRepository
import org.orbitfs.android.data.SavedHost
import org.orbitfs.android.model.FileInfo
import java.io.File
import java.io.IOException

data class BrowserState(
    val currentPath: String = "",
    val files: List<FileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class FileBrowserViewModel(
    private val connectionManager: ConnectionManager,
    private val hostRepository: HostRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state

    val currentPath: String
        get() = _state.value.currentPath

    init {
        viewModelScope.launch {
            connectionManager.connectionState.collect { connState ->
                if (connState is ConnectionState.Connected) {
                    refreshCurrentPath()
                }
            }
        }
    }

    fun refreshCurrentPath() {
        loadFiles(_state.value.currentPath)
    }

    fun navigateTo(path: String) {
        if (path == "..") {
            navigateToParent()
        } else {
            loadFiles(path)
        }
    }

    private fun navigateToParent() {
        val current = _state.value.currentPath
        val parent = computeParentPath(current)
        if (parent != current) {
            loadFiles(parent)
        }
    }

    private fun loadFiles(path: String) {
        _state.update { it.copy(isLoading = true, error = null, currentPath = path) }

        viewModelScope.launch {
            try {
                val client = connectionManager.getClient()
                val files = client.withRetry { client.list(path) }
                _state.update {
                    it.copy(
                        files = files,
                        isLoading = false,
                        currentPath = path,
                        error = null
                    )
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

    fun openFile(fileInfo: FileInfo, context: Context) {
        viewModelScope.launch {
            try {
                val client = connectionManager.getClient()
                val data = client.withRetry { client.readFile(fileInfo.path) }
                withContext(Dispatchers.Main) {
                    val tmpFile = File(context.cacheDir, fileInfo.name)
                    tmpFile.writeBytes(data)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tmpFile
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, getMimeType(fileInfo.name))
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "No app to open ${fileInfo.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to open: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
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

    fun deleteHost(id: String) {
        hostRepository.removeHost(id)
    }
}

private fun getMimeType(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".txt") -> "text/plain"
        lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
        lower.endsWith(".json") -> "application/json"
        lower.endsWith(".xml") -> "application/xml"
        lower.endsWith(".zip") -> "application/zip"
        else -> "*/*"
    }
}

fun computeParentPath(current: String): String {
    if (current.isEmpty() || current == "/") return ""
    val trimmed = current.removeSuffix("/")
    val lastSlash = trimmed.lastIndexOf("/")
    return if (lastSlash <= 0) "" else trimmed.substring(0, lastSlash)
}
