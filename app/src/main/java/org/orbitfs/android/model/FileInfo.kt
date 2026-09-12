package org.orbitfs.android.model

data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long = System.currentTimeMillis(),
    val created: Long = 0L,
    val extension: String = "",
    val mimeType: String = "",
    val permissions: String = "",
    val owner: String = ""
) {
    val displayName: String get() = if (isDirectory && !name.endsWith("/")) "$name/" else name

    fun getFileType(): String {
        if (extension.isEmpty()) return if (isDirectory) "Folder" else "Unknown"
        return extension.uppercase()
    }
}
