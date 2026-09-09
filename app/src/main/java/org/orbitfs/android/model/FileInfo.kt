package org.orbitfs.android.model

data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long
) {
    val displayName: String get() = if (isDirectory) "$name/" else name
}
