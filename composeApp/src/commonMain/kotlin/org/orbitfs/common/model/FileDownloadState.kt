package org.orbitfs.common.model

data class FileDownloadState(
    val path: String,
    val fileName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: DownloadStatus,
    val serverAddress: String = "",
    val savedToPath: String = "",
    val errorMessage: String = "",
    val speedBytesPerSecond: Long = 0L,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val progressFraction: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}