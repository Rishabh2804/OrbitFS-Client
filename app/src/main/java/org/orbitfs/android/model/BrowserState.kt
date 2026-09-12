package org.orbitfs.android.model

data class BrowserState(
    val currentPath: String = "",
    val files: List<FileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showHiddenFiles: Boolean = false,
    val isMultiSelect: Boolean = false,
    val selectedPaths: Set<String> = emptySet()
)