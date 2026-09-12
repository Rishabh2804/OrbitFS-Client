package org.orbitfs.android.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.orbitfs.android.model.FileDownloadState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class DownloadEntry(
    val id: String,
    val fileName: String,
    val filePath: String,
    val totalBytes: Long,
    val bytesDownloaded: Long,
    val status: String,
    val timestamp: Long,
    val savedPath: String? = null
) {
    val progressFraction: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
    val statusLabel: String get() = when (status) {
        "in_progress" -> "Downloading"
        "complete" -> "Completed"
        "failed" -> "Failed"
        else -> status
    }
}

class DownloadsRepository {
    private val _downloads = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val downloads: StateFlow<List<DownloadEntry>> = _downloads

    fun addDownload(entry: DownloadEntry) {
        _downloads.update { it + entry }
    }

    fun updateDownload(id: String, bytesDownloaded: Long, totalBytes: Long, status: String) {
        _downloads.update { list ->
            list.map { entry ->
                if (entry.id == id) {
                    entry.copy(
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        status = status
                    )
                } else entry
            }
        }
    }

    fun completeDownload(id: String, savedPath: String) {
        _downloads.update { list ->
            list.map { entry ->
                if (entry.id == id) {
                    entry.copy(
                        bytesDownloaded = entry.totalBytes,
                        status = "complete",
                        savedPath = savedPath
                    )
                } else entry
            }
        }
    }

    fun failDownload(id: String, error: String) {
        _downloads.update { list ->
            list.map { entry ->
                if (entry.id == id) {
                    entry.copy(status = "failed")
                } else entry
            }
        }
    }

    fun removeDownload(id: String) {
        _downloads.update { it.filterNot { d -> d.id == id } }
    }

    fun clearCompleted() {
        _downloads.update { list -> list.filterNot { it.status == "complete" } }
    }

    fun clearAll() {
        _downloads.value = emptyList()
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
