package org.orbitfs.common.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onOpenDownloadedFile: (FileDownloadState) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
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
                    if (selectedTab == 1 && historyTransfers.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear History")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Active (${activeTransfers.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("History (${historyTransfers.size})") }
                )
            }

            if (selectedTab == 0) {
                ActiveTransfersList(activeTransfers, onCancelTransfer)
            } else {
                HistoryTransfersList(historyTransfers, onRetryTransfer, onOpenDownloadedFile)
            }
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
    onOpen: (FileDownloadState) -> Unit
) {
    if (transfers.isEmpty()) {
        EmptyState("No transfer history", Icons.Rounded.History)
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(transfers, key = { it.path }) { state ->
                HistoryTransferItem(state, onRetry, onOpen)
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
                    Text(state.serverAddress, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("${MimeTypeUtil.formatFileSize(state.speedBytesPerSecond)}/s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun HistoryTransferItem(
    state: FileDownloadState, 
    onRetry: (FileDownloadState) -> Unit,
    onOpen: (FileDownloadState) -> Unit
) {
    val color = when (state.status) {
        DownloadStatus.COMPLETE -> ColorStatusGreen
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = { if (state.status == DownloadStatus.COMPLETE) onOpen(state) },
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
                Text(state.status.name, style = MaterialTheme.typography.labelSmall, color = color)
            }
            if (state.status != DownloadStatus.COMPLETE) {
                IconButton(onClick = { onRetry(state) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
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
