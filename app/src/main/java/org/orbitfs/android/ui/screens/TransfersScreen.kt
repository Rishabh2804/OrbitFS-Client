package org.orbitfs.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.orbitfs.android.model.FileDownloadState
import org.orbitfs.android.model.DownloadStatus
import org.orbitfs.android.ui.theme.*
import org.orbitfs.android.util.MimeTypeUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    downloadStates: List<FileDownloadState>,
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
    onCancelTransfer: (String) -> Unit,
    onRetryTransfer: (FileDownloadState) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val activeTransfers = downloadStates.filter { it.status == DownloadStatus.IN_PROGRESS || it.status == DownloadStatus.NOT_STARTED }
    val historyTransfers = downloadStates.filter { it.status == DownloadStatus.COMPLETE || it.status == DownloadStatus.FAILED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedTab == 1 && historyTransfers.isNotEmpty()) {
                        TextButton(onClick = onClearHistory) {
                            Text("Clear History")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Active")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("History")
                        }
                    }
                )
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    0 -> ActiveTransfersList(activeTransfers, onCancelTransfer)
                    1 -> HistoryTransfersList(historyTransfers, onRetryTransfer)
                }
            }
        }
    }
}

@Composable
fun ActiveTransfersList(transfers: List<FileDownloadState>, onCancel: (String) -> Unit) {
    if (transfers.isEmpty()) {
        EmptyState(message = "No active transfers")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(transfers) { state ->
                ActiveTransferItem(state, onCancel)
            }
        }
    }
}

@Composable
fun HistoryTransfersList(transfers: List<FileDownloadState>, onRetry: (FileDownloadState) -> Unit) {
    if (transfers.isEmpty()) {
        EmptyState(message = "Your transfer history is empty")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(transfers) { state ->
                HistoryTransferItem(state, onRetry)
            }
        }
    }
}

@Composable
fun ActiveTransferItem(state: FileDownloadState, onCancel: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("to Downloads", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onCancel(state.path) }) {
                    Icon(Icons.Rounded.Cancel, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { state.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${MimeTypeUtil.formatFileSize(state.bytesDownloaded)} / ${MimeTypeUtil.formatFileSize(state.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${MimeTypeUtil.formatFileSize(state.speedBytesPerSecond)}/s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun HistoryTransferItem(state: FileDownloadState, onRetry: (FileDownloadState) -> Unit) {
    val isSuccess = state.status == DownloadStatus.COMPLETE
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSuccess) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isSuccess) Icons.Rounded.Download else Icons.Rounded.Cancel,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(state.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isSuccess) "Completed" else "Failed: ${state.errorMessage ?: "Unknown error"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSuccess) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
        
        if (!isSuccess) {
            TextButton(onClick = { onRetry(state) }) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
fun TransfersPreview() {
    OrbitFSTheme {
        TransfersScreen(
            downloadStates = listOf(
                FileDownloadState(
                    path = "1", fileName = "vlc-setup.exe", bytesDownloaded = 15000000, totalBytes = 45000000,
                    status = DownloadStatus.IN_PROGRESS, speedBytesPerSecond = 1200000
                ),
                FileDownloadState(
                    path = "2", fileName = "holiday_photos.zip", bytesDownloaded = 80000000, totalBytes = 80000000,
                    status = DownloadStatus.COMPLETE
                )
            ),
            onBack = {},
            onClearHistory = {},
            onCancelTransfer = {},
            onRetryTransfer = {}
        )
    }
}
