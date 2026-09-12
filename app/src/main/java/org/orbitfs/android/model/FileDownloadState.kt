package org.orbitfs.android.model

data class FileDownloadState(
    val path: String,
    val fileName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: DownloadStatus
) {
    val progressFraction: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}