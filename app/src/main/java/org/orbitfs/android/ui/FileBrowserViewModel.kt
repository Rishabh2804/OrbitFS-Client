package org.orbitfs.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.client.ConnectionState
import org.orbitfs.android.data.ConnectionConfig
import org.orbitfs.android.model.FileInfo
import java.io.IOException

    data class BrowserState(
    val currentPath: String = "",
    val files: List<FileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val config: ConnectionConfig = ConnectionConfig()
)

class FileBrowserViewModel(
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state

    init {
        viewModelScope.launch {
            connectionManager.connectionState.collect { connState ->
                _state.update { s ->
                    s.copy(
                        connectionState = connState,
                        config = connectionManager.currentConfig
                    )
                }
                if (connState is ConnectionState.Connected) {
                    refreshCurrentPath()
                }
            }
        }
    }

    fun refreshCurrentPath() {
        val path = _state.value.currentPath
        loadFiles(path)
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
                val files = client.withRetry {
                    client.list(path)
                }
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
}

fun computeParentPath(current: String): String {
    if (current.isEmpty() || current == "/") return ""
    val trimmed = current.removeSuffix("/")
    val lastSlash = trimmed.lastIndexOf("/")
    return if (lastSlash <= 0) "" else trimmed.substring(0, lastSlash)
}
