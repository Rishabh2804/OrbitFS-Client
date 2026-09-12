package org.orbitfs.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.orbitfs.android.model.BrowserState
import org.orbitfs.android.model.DownloadStatus
import org.orbitfs.android.model.FileDownloadState
import org.orbitfs.android.model.FileInfo
import org.orbitfs.android.util.MimeTypeUtil
import org.orbitfs.android.util.debouncedClick

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserContent(
    state: BrowserState,
    downloadStates: Map<String, FileDownloadState>,
    onNavigate: (String) -> Unit,
    onOpenFile: (FileInfo) -> Unit,
    onFileLongPress: (FileInfo) -> Unit,
    onFileClick: (FileInfo) -> Unit,
    onFileSelectToggle: (String) -> Unit,
    onRefresh: () -> Unit,
    onDownloadClick: (FileInfo) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.isLoading && state.files.isEmpty()) {
            LoadingContent()
        } else if (state.error != null && state.files.isEmpty()) {
            ErrorStateContent(error = state.error!!)
        } else {
            FileListContent(
                files = state.files,
                path = state.currentPath,
                isLoading = state.isLoading,
                isMultiSelect = state.isMultiSelect,
                selectedPaths = state.selectedPaths,
                downloadStates = downloadStates,
                onNavigate = onNavigate,
                onOpenFile = onOpenFile,
                onFileLongPress = onFileLongPress,
                onFileClick = onFileClick,
                onFileSelectToggle = onFileSelectToggle,
                onRefresh = onRefresh,
                onDownloadClick = onDownloadClick
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ErrorStateContent(error: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun FileListContent(
    files: List<FileInfo>,
    path: String,
    isLoading: Boolean,
    isMultiSelect: Boolean,
    selectedPaths: Set<String>,
    downloadStates: Map<String, FileDownloadState>,
    onNavigate: (String) -> Unit,
    onOpenFile: (FileInfo) -> Unit,
    onFileLongPress: (FileInfo) -> Unit,
    onFileClick: (FileInfo) -> Unit,
    onFileSelectToggle: (String) -> Unit,
    onRefresh: () -> Unit,
    onDownloadClick: (FileInfo) -> Unit
) {
    if (files.isEmpty() && path.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = "Empty directory",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val pullToRefreshState = rememberPullToRefreshState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullToRefresh(
                    isRefreshing = isLoading,
                    state = pullToRefreshState,
                    onRefresh = onRefresh,
                    enabled = !isMultiSelect
                )
        ) {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (path.isNotEmpty()) {
                    item {
                        FileRow(
                            file = FileInfo(
                                name = "..",
                                path = "..",
                                size = 0L,
                                isDirectory = true,
                                lastModified = 0L
                            ),
                            isMultiSelect = false,
                            isSelected = false,
                            downloadState = null,
                            onClick = { onNavigate("..") },
                            onLongPress = { },
                            onSelectToggle = { }
                        )
                    }
                }
                items(files, key = { it.path }) { file ->
                    FileRow(
                        file = file,
                        isMultiSelect = isMultiSelect,
                        isSelected = selectedPaths.contains(file.path),
                        downloadState = downloadStates[file.path],
                        onClick = {
                            if (isMultiSelect) {
                                onFileSelectToggle(file.path)
                            } else if (file.isDirectory) {
                                onNavigate(file.path)
                            } else {
                                onFileClick(file)
                            }
                        },
                        onLongPress = {
                            onFileLongPress(file)
                        },
                        onSelectToggle = { onFileSelectToggle(file.path) },
                        onDownloadClick = { onDownloadClick(it) }
                    )
                }
            }
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isLoading,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun FileRow(
    file: FileInfo,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    downloadState: FileDownloadState?,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onSelectToggle: () -> Unit = {},
    onDownloadClick: (FileInfo) -> Unit = {}
) {
    val debouncedDownload = debouncedClick { onDownloadClick(file) }
    val debouncedToggle = debouncedClick { onSelectToggle() }
    val debouncedOnClick = debouncedClick { onClick() }
    val debouncedLongPress = debouncedClick { onLongPress() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .combinedClickable(
                onClick = debouncedOnClick,
                onLongClick = debouncedLongPress
            ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { debouncedToggle() },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
            }

            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Folder,
                contentDescription = null,
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.size(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!file.isDirectory) {
                    Text(
                        text = MimeTypeUtil.formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isMultiSelect && !file.isDirectory) {
                val ds = downloadState
                when (ds?.status) {
                    DownloadStatus.NOT_STARTED, null -> {
                        IconButton(
                            onClick = { debouncedDownload() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Load file",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DownloadStatus.IN_PROGRESS -> {
                        CircularProgressIndicator(
                            progress = { ds.progressFraction },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    DownloadStatus.FAILED -> {
                        IconButton(
                            onClick = { debouncedDownload() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Retry load",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DownloadStatus.COMPLETE -> {}
                }
            }
        }
    }
}
