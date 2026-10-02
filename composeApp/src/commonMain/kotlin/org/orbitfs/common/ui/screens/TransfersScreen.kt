package org.orbitfs.common.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.orbitfs.common.model.DownloadStatus
import org.orbitfs.common.model.FileDownloadState
import org.orbitfs.common.ui.theme.*
import org.orbitfs.common.util.MimeTypeUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    downloadStates: List<FileDownloadState>,
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
    onCancelTransfer: (String) -> Unit,
    onRetryTransfer: (FileDownloadState) -> Unit,
    onOpenDownloadedFile: (FileDownloadState) -> Unit = {},
    onDeleteHistoryItem: (String, Boolean) -> Unit = { _, _ -> },
    onOpenFolder: (FileDownloadState) -> Unit = {}
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    var selectedDetailItem by remember { mutableStateOf<FileDownloadState?>(null) }
    var itemToDelete by remember { mutableStateOf<FileDownloadState?>(null) }
    
    val activeTransfers = downloadStates.filter { it.status == DownloadStatus.IN_PROGRESS || it.status == DownloadStatus.NOT_STARTED }
    val historyTransfers = downloadStates.filter { it.status != DownloadStatus.IN_PROGRESS && it.status != DownloadStatus.NOT_STARTED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfers", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (pagerState.currentPage == 1 && historyTransfers.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear History")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Active (${activeTransfers.size})") }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("History (${historyTransfers.size})") }
                )
            }

            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                if (page == 0) {
                    ActiveTransfersList(activeTransfers, onCancelTransfer)
                } else {
                    HistoryTransfersList(
                        transfers = historyTransfers,
                        onRetry = onRetryTransfer,
                        onOpen = onOpenDownloadedFile,
                        onOpenFolder = onOpenFolder,
                        onShowInfo = { selectedDetailItem = it },
                        onDelete = { itemToDelete = it }
                    )
                }
            }
        }

        if (selectedDetailItem != null) {
            TransferDetailsPopup(
                state = selectedDetailItem!!,
                onClose = { selectedDetailItem = null },
                onOpen = {
                    onOpenDownloadedFile(selectedDetailItem!!)
                    selectedDetailItem = null
                },
                onOpenFolder = {
                    onOpenFolder(selectedDetailItem!!)
                }
            )
        }

        if (itemToDelete != null) {
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("Remove Transfer", fontWeight = FontWeight.Bold) },
                text = { Text("Do you also want to delete the downloaded file from local storage?") },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteHistoryItem(itemToDelete!!.path, true)
                        itemToDelete = null
                    }) {
                        Text("Delete File & Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        onDeleteHistoryItem(itemToDelete!!.path, false)
                        itemToDelete = null
                    }) {
                        Text("Remove From History Only")
                    }
                }
            )
        }
    }
}

@Composable
fun ActiveTransfersList(transfers: List<FileDownloadState>, onCancel: (String) -> Unit) {
    if (transfers.isEmpty()) {
        EmptyState("No active transfers", Icons.Rounded.Download)
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(transfers, key = { it.path }) { state ->
                ActiveTransferItem(state, onCancel)
            }
        }
    }
}

@Composable
fun HistoryTransfersList(
    transfers: List<FileDownloadState>, 
    onRetry: (FileDownloadState) -> Unit,
    onOpen: (FileDownloadState) -> Unit,
    onOpenFolder: (FileDownloadState) -> Unit,
    onShowInfo: (FileDownloadState) -> Unit,
    onDelete: (FileDownloadState) -> Unit
) {
    if (transfers.isEmpty()) {
        EmptyState("No transfer history", Icons.Rounded.History)
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(transfers, key = { it.path }) { state ->
                HistoryTransferItem(state, onRetry, onOpen, onOpenFolder, onShowInfo, onDelete)
            }
        }
    }
}

@Composable
fun ActiveTransferItem(state: FileDownloadState, onCancel: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.fileName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (state.serverAddress.isNotEmpty()) state.serverAddress else "Satellite Transfer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onCancel(state.path) }) {
                    Icon(Icons.Rounded.Cancel, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progressFraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${MimeTypeUtil.formatFileSize(state.bytesDownloaded)} / ${MimeTypeUtil.formatFileSize(state.totalBytes)}", style = MaterialTheme.typography.labelSmall)
                Text("${MimeTypeUtil.formatFileSize(state.speedBytesPerSecond)}/s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HistoryTransferItem(
    state: FileDownloadState, 
    onRetry: (FileDownloadState) -> Unit,
    onOpen: (FileDownloadState) -> Unit,
    onOpenFolder: (FileDownloadState) -> Unit,
    onShowInfo: (FileDownloadState) -> Unit,
    onDelete: (FileDownloadState) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val color = when (state.status) {
        DownloadStatus.COMPLETE -> ColorStatusGreen
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = { if (state.status == DownloadStatus.COMPLETE) onOpen(state) else onShowInfo(state) },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (state.status) {
                        DownloadStatus.COMPLETE -> Icons.Rounded.CheckCircle
                        DownloadStatus.FAILED -> Icons.Rounded.Error
                        else -> Icons.Rounded.History
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.fileName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.status.name, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (state.status == DownloadStatus.COMPLETE) MimeTypeUtil.formatFileSize(state.totalBytes)
                        else "${MimeTypeUtil.formatFileSize(state.bytesDownloaded)} / ${MimeTypeUtil.formatFileSize(state.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (state.status == DownloadStatus.COMPLETE) {
                        DropdownMenuItem(
                            text = { Text("Open File") },
                            leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onOpen(state)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Show in Folder") },
                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onOpenFolder(state)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Information") },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShowInfo(state)
                        }
                    )
                    if (state.status == DownloadStatus.FAILED || state.status == DownloadStatus.CANCELLED) {
                        DropdownMenuItem(
                            text = { Text("Retry") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRetry(state)
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete(state)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TransferDetailsPopup(
    state: FileDownloadState,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onOpenFolder: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Transfer Details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(state.fileName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    "Status: ${state.status.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (state.status) {
                        DownloadStatus.COMPLETE -> ColorStatusGreen
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(20.dp))

                Column {
                    Text("Transfer Info", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Size", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(MimeTypeUtil.formatFileSize(state.totalBytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Downloaded", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(MimeTypeUtil.formatFileSize(state.bytesDownloaded), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Date", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(MimeTypeUtil.formatDate(state.lastUpdated), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }

                if (state.errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Error Details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(state.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(20.dp))

                val displayLoc = state.savedToPath.ifEmpty { "Default Downloads/OrbitFS" }
                Text("Saved Location", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayLoc,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onOpenFolder) {
                            Icon(
                                Icons.Rounded.FolderOpen,
                                contentDescription = "Show in Folder",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(displayLoc))
                                copied = true
                            }
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (copied) ColorStatusGreen else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.status == DownloadStatus.COMPLETE) {
                        Button(
                            onClick = onOpen,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Open File")
                        }
                    }
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String, icon: ImageVector) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
