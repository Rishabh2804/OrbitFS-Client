package org.orbitfs.android.model

enum class SortType { Name, Date, Size }
enum class SortOrder { Ascending, Descending }

data class BrowserState(
    val currentPath: String = "",
    val files: List<FileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val showHiddenFiles: Boolean = false,
    val isMultiSelect: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val sortType: SortType = SortType.Name,
    val sortOrder: SortOrder = SortOrder.Ascending,
    val fileToConfirmLargeDownload: FileInfo? = null
)
