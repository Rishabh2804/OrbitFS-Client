package org.orbitfs.android.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.orbitfs.android.model.BrowserState
import org.orbitfs.android.model.FileInfo
import org.orbitfs.android.ui.theme.*
import org.orbitfs.android.util.MimeTypeUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    state: BrowserState,
    serverName: String,
    serverAddress: String,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onBreadcrumbClick: (String) -> Unit,
    onItemClick: (FileInfo) -> Unit,
    onItemLongClick: (FileInfo) -> Unit,
    onItemSelectToggle: (FileInfo) -> Unit,
    onClearSelection: () -> Unit,
    onUploadFile: () -> Unit,
    onNewDir: () -> Unit,
    onViewStat: (FileInfo) -> Unit,
    onSaveToDevice: (FileInfo) -> Unit,
    onShareFile: (FileInfo) -> Unit,
    onDeleteSelected: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    val topBarBgColor by animateColorAsState(
        targetValue = if (state.isMultiSelect) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        label = "TopBarBg"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.isMultiSelect) {
                        Text(
                            "${state.selectedPaths.size} Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Column {
                            Text(
                                serverName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                serverAddress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (state.isMultiSelect) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (state.isMultiSelect) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                val selectedFiles = state.files.filter { it.path in state.selectedPaths }
                                val isSingle = selectedFiles.size == 1
                                
                                DropdownMenuItem(
                                    text = { Text("Information") },
                                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        if (isSingle) onViewStat(selectedFiles.first())
                                    },
                                    enabled = isSingle
                                )
                                DropdownMenuItem(
                                    text = { Text("Save to Device") },
                                    leadingIcon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        if (isSingle) onSaveToDevice(selectedFiles.first())
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        if (isSingle) onShareFile(selectedFiles.first())
                                    },
                                    enabled = isSingle && !selectedFiles.first().isDirectory
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDeleteSelected()
                                    }
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarBgColor
                )
            )
        },
        floatingActionButton = {
            if (!state.isMultiSelect) {
                Column(horizontalAlignment = Alignment.End) {
                    SmallFloatingActionButton(
                        onClick = onNewDir,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "New Folder")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    FloatingActionButton(
                        onClick = onUploadFile,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Upload, contentDescription = "Upload")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            BreadcrumbBar(currentPath = state.currentPath, onBreadcrumbClick = onBreadcrumbClick)
            
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(state.files) { file ->
                        FileRowItem(
                            file = file,
                            isSelected = state.selectedPaths.contains(file.path),
                            onClick = { onItemClick(file) },
                            onLongClick = { onItemLongClick(file) },
                            onSelectToggle = { onItemSelectToggle(file) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BreadcrumbBar(currentPath: String, onBreadcrumbClick: (String) -> Unit) {
    val segments = remember(currentPath) {
        val parts = currentPath.split("/").filter { it.isNotEmpty() }
        listOf("root") + parts
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(segments.size) { index ->
            val segment = segments[index]
            TextButton(
                onClick = {
                    if (index == 0) onBreadcrumbClick("")
                    else onBreadcrumbClick(segments.subList(1, index + 1).joinToString("/", prefix = "/"))
                },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier.heightIn(min = 24.dp)
            ) {
                Text(
                    text = segment,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index == segments.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (index < segments.size - 1) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRowItem(
    file: FileInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectToggle: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simplified Checkbox / Selection state
            if (isSelected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                    contentDescription = null,
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) 
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (file.isDirectory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
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
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FileExplorerPreview() {
    OrbitFSTheme {
        FileExplorerScreen(
            state = BrowserState(
                currentPath = "/work/projects/orbit",
                files = listOf(
                    FileInfo("documents", "/work/projects/orbit/documents", 0, true),
                    FileInfo("source_code", "/work/projects/orbit/source_code", 0, true),
                    FileInfo("README.md", "/work/projects/orbit/README.md", 1240, false),
                    FileInfo("design_spec.pdf", "/work/projects/orbit/design_spec.pdf", 5600000, false)
                )
            ),
            serverName = "Local Desktop",
            serverAddress = "192.168.1.15",
            onBack = {},
            onSearchClick = {},
            onBreadcrumbClick = {},
            onItemClick = {},
            onItemLongClick = {},
            onItemSelectToggle = {},
            onClearSelection = {},
            onUploadFile = {},
            onNewDir = {},
            onViewStat = {},
            onSaveToDevice = {},
            onShareFile = {},
            onDeleteSelected = {}
        )
    }
}
