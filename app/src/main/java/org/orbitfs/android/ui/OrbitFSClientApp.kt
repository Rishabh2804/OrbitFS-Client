package org.orbitfs.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.client.ConnectionState
import org.orbitfs.android.data.ConnectionConfig
import org.orbitfs.android.data.HostRepository
import org.orbitfs.android.data.SavedHost
import org.orbitfs.android.model.DownloadStatus
import org.orbitfs.android.model.FileDownloadState
import org.orbitfs.android.model.FileInfo
import org.orbitfs.android.util.MimeTypeUtil
import org.orbitfs.android.util.debouncedClick

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OrbitFSRoot(
    viewModel: FileBrowserViewModel,
    connectionManager: ConnectionManager,
    hostRepository: HostRepository,
    settingsRepository: org.orbitfs.android.data.SettingsRepository
) {
    val connectionState by connectionManager.connectionState.collectAsStateWithLifecycle()
    val hosts by hostRepository.hosts.collectAsStateWithLifecycle()
    val browserState by viewModel.state.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()

    var showAddHostDialog by rememberSaveable { mutableStateOf(false) }
    var editingHost by rememberSaveable { mutableStateOf<SavedHost?>(null) }
    var deletingHost by rememberSaveable { mutableStateOf<SavedHost?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDirInfoDialog by remember { mutableStateOf(false) }
    var showSelectedInfoDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var filesToDelete by remember { mutableStateOf<List<String>>(emptyList()) }
    val context = LocalContext.current

    val currentPath = browserState.currentPath
    val displayTitle = if (currentPath.isEmpty()) {
        "OrbitFS"
    } else {
        currentPath.removeSuffix("/").substringAfterLast("/")
    }
    val isHostView = connectionState !is ConnectionState.Connected
    val allSelected = browserState.selectedPaths.containsAll(
        browserState.files.map { it.path }
    )

    Scaffold(
        topBar = {
            if (browserState.isMultiSelect) {
                TopAppBar(
                     title = {
                         Text(
                             text = "${browserState.selectedPaths.size} selected",
                             maxLines = 1,
                             overflow = TextOverflow.Ellipsis,
                             color = MaterialTheme.colorScheme.onPrimary
                         )
                     },
                     navigationIcon = {
                         Row {
                             IconButton(onClick = { viewModel.clearSelection() }) {
                                 Icon(
                                     imageVector = Icons.Default.Close,
                                     contentDescription = "Cancel selection",
                                     tint = MaterialTheme.colorScheme.onPrimary
                                 )
                             }
                              IconButton(
                                  onClick = {
                                      if (allSelected) {
                                          viewModel.deselectAll()
                                      } else {
                                          viewModel.selectAll()
                                      }
                                  }
                             ) {
                                 Icon(
                                     imageVector = if (allSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                     contentDescription = if (allSelected) "Deselect all" else "Select all",
                                     tint = MaterialTheme.colorScheme.onPrimary
                                 )
                             }
                         }
                     },
                      actions = {
                          IconButton(
                              onClick = debouncedClick {
                                  val selectedFiles = browserState.files.filter { it.path in browserState.selectedPaths && !it.isDirectory }
                                  selectedFiles.forEach { viewModel.downloadFile(it, context) }
                              }
                          ) {
                              Icon(
                                  imageVector = Icons.Default.Download,
                                  contentDescription = "Download selected",
                                  tint = MaterialTheme.colorScheme.onPrimary
                              )
                          }
                         Box {
                             IconButton(onClick = { showMenu = true }) {
                                 Icon(
                                     imageVector = Icons.Default.MoreVert,
                                     contentDescription = "Bulk actions",
                                     tint = MaterialTheme.colorScheme.onPrimary
                                 )
                             }
                              DropdownMenu(
                                  expanded = showMenu,
                                  onDismissRequest = { showMenu = false }
                              ) {
                                  DropdownMenuItem(
                                      text = { Text("Info") },
                                      onClick = {
                                          showMenu = false
                                          showSelectedInfoDialog = true
                                      }
                                  )
                                  DropdownMenuItem(
                                      text = { Text("Copy Path") },
                                      onClick = {
                                          showMenu = false
                                          val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                          val normalizedPaths = browserState.selectedPaths.joinToString("\n") { path ->
                                              if (path.startsWith("/")) path else "/$path"
                                          }
                                          val clip = android.content.ClipData.newPlainText("paths", normalizedPaths)
                                          clipboard.setPrimaryClip(clip)
                                      }
                                 )
                                 DropdownMenuItem(
                                     text = {
                                         Text(
                                             text = "Delete",
                                             color = MaterialTheme.colorScheme.error
                                         )
                                     },
                                     onClick = {
                                         showMenu = false
                                         val selected = browserState.selectedPaths.toList()
                                         if (selected.isNotEmpty()) {
                                             showDeleteConfirm = true
                                             filesToDelete = selected
                                         }
                                     }
                                 )
                             }
                         }
                     },
                     colors = TopAppBarDefaults.topAppBarColors(
                         containerColor = MaterialTheme.colorScheme.primary
                     )
                 )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (isHostView) "OrbitFS" else displayTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        },
                        navigationIcon = {
                            if (!isHostView && currentPath.isNotEmpty()) {
                                IconButton(onClick = { viewModel.navigateTo("..") }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Go back",
                                        tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        if (showDownloadsDialog) {
            DownloadsDialog(
                showDialog = showDownloadsDialog,
                onDismissRequest = { showDownloadsDialog = false },
                downloadStates = downloadStates,
                onCancelDownload = { path -> viewModel.cancelDownload(path) }
            )
        }
    }
                        },
                        actions = {
                            if (isHostView) {
                                IconButton(onClick = { showAddHostDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add host",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Menu",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Settings") },
                                            onClick = {
                                                showMenu = false
                                                viewModel.showSettings()
                                            }
                                        )
                                    }
                                }
                            } else {
                                IconButton(
                                    onClick = { showDirInfoDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Directory Info",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Menu",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Save to Downloads") },
                                            onClick = {
                                                showMenu = false
                                                viewModel.saveSelectedToDownloads(context)
                                            },
                                            enabled = !browserState.isMultiSelect || browserState.selectedPaths.any { path ->
                                                !(browserState.files.find { it.path == path }?.isDirectory ?: true)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Download") },
                                            onClick = {
                                                showMenu = false
                                                viewModel.downloadSelectedFiles(context)
                                            },
                                            enabled = !browserState.isMultiSelect || browserState.selectedPaths.any { path ->
                                                !(browserState.files.find { it.path == path }?.isDirectory ?: true)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Info") },
                                            onClick = {
                                                showMenu = false
                                                showSelectedInfoDialog = true
                                            },
                                            enabled = browserState.isMultiSelect && browserState.selectedPaths.isNotEmpty()
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Copy Path") },
                                            onClick = {
                                                showMenu = false
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val normalizedPaths = browserState.selectedPaths.joinToString("\n") { path ->
                                                    if (path.startsWith("/")) path else "/$path"
                                                }
                                                val clip = android.content.ClipData.newPlainText("paths", normalizedPaths)
                                                clipboard.setPrimaryClip(clip)
                                                viewModel.clearSelection()
                                            },
                                            enabled = browserState.isMultiSelect && browserState.selectedPaths.isNotEmpty()
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Show Hidden Files") },
                                            onClick = {
                                                showMenu = false
                                                viewModel.toggleHiddenFiles()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Downloads") },
                                            onClick = {
                                                showMenu = false
                                                showDownloadsDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Refresh") },
                                            onClick = {
                                                showMenu = false
                                                viewModel.refreshCurrentPath()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Close") },
                                            onClick = {
                                                showMenu = false
                                                viewModel.disconnect()
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (val state = connectionState) {
                    is ConnectionState.Disconnected -> {
                        HostListContent(
                            hosts = hosts,
                            onHostClick = { host ->
                                viewModel.connectToSavedHost(host)
                            },
                            onDeleteHost = { id ->
                                val host = hosts.find { it.id == id }
                                if (host != null) deletingHost = host
                            },
                            onEditHost = { host ->
                                editingHost = host
                            },
                            onAddHost = { showAddHostDialog = true }
                        )
                    }

                    is ConnectionState.Connecting -> {
                        ConnectingContent(config = state.config)
                    }

                    is ConnectionState.Connected -> {
                        val ctx = LocalContext.current
                        FileBrowserContent(
                            state = browserState,
                            downloadStates = downloadStates,
                            onNavigate = { path -> viewModel.navigateTo(path) },
                            onOpenFile = { fileInfo -> viewModel.openFile(fileInfo, ctx) },
                            onFileLongPress = { file ->
                                viewModel.selectFile(file.path)
                                viewModel.toggleMultiSelect()
                            },
                            onFileClick = { fileInfo -> viewModel.openFile(fileInfo, ctx) },
                            onFileSelectToggle = { path -> viewModel.selectFile(path) },
                            onRefresh = { viewModel.refreshCurrentPath() },
                            onDownloadClick = { fileInfo -> viewModel.downloadFile(fileInfo, ctx) }
                        )
                    }

                    is ConnectionState.Error -> {
                        ErrorContent(
                            message = state.message,
                            attempt = state.attempt,
                            maxAttempts = state.maxAttempts,
                            onRetry = { viewModel.retryConnection() },
                            config = state.config
                        )
                    }

                    is ConnectionState.GaveUp -> {
                        GaveUpContent(
                            message = state.message,
                            onRetry = { viewModel.retryConnection() }
                        )
                    }
                }
            }
        }

        if (showAddHostDialog) {
            AddEditHostDialog(
                showDialog = showAddHostDialog,
                onDismissRequest = { showAddHostDialog = false },
                onConfirm = { name, host, port ->
                    viewModel.addHost(name, host, port)
                    showAddHostDialog = false
                }
            )
        }

        if (editingHost != null) {
            val hostToEdit = editingHost!!
            AddEditHostDialog(
                showDialog = true,
                initialName = hostToEdit.name,
                initialHost = hostToEdit.host,
                initialPort = hostToEdit.port.toString(),
                onDismissRequest = { editingHost = null },
                onConfirm = { name, host, port ->
                    viewModel.updateHost(hostToEdit.id, name, host, port)
                    editingHost = null
                }
            )
        }

        if (deletingHost != null) {
            AlertDialog(
                onDismissRequest = { deletingHost = null },
                title = { Text("Delete Host") },
                text = {
                    Text(
                        "Are you sure you want to delete \"${deletingHost!!.name}\"?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteHost(deletingHost!!.id)
                        deletingHost = null
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingHost = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Files") },
                text = {
                    Text("Are you sure you want to delete ${filesToDelete.size} file(s)?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteFiles(filesToDelete)
                            showDeleteConfirm = false
                            filesToDelete = emptyList()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDirInfoDialog) {
            AlertDialog(
                onDismissRequest = { showDirInfoDialog = false },
                title = { Text("Directory Info") },
                text = {
                    val totalFiles = browserState.files.count { !it.isDirectory }
                    val totalDirs = browserState.files.count { it.isDirectory }
                    val totalSize = browserState.files.filter { !it.isDirectory }.sumOf { it.size }
                    val largestFile = browserState.files.filter { !it.isDirectory }.maxByOrNull { it.size }
                    Column {
                        Text("Path: ${if (currentPath.isEmpty()) "/" else currentPath}", style = MaterialTheme.typography.bodyMedium)
                        Text("Directories: $totalDirs", style = MaterialTheme.typography.bodySmall)
                        Text("Files: $totalFiles", style = MaterialTheme.typography.bodySmall)
                        Text("Total size: ${MimeTypeUtil.formatFileSize(totalSize)}", style = MaterialTheme.typography.bodySmall)
                        largestFile?.let {
                            Text("Largest: ${it.displayName} (${MimeTypeUtil.formatFileSize(it.size)})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDirInfoDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        if (showSelectedInfoDialog) {
            val selectedFiles = browserState.files.filter { it.path in browserState.selectedPaths }
            AlertDialog(
                onDismissRequest = { showSelectedInfoDialog = false },
                title = { Text("Selected Items Info") },
                text = {
                    val totalSize = selectedFiles.filter { !it.isDirectory }.sumOf { it.size }
                    val dirsCount = selectedFiles.count { it.isDirectory }
                    val filesCount = selectedFiles.count { !it.isDirectory }
                    Column {
                        Text("Selected: ${selectedFiles.size} items", style = MaterialTheme.typography.bodyMedium)
                        Text("Directories: $dirsCount", style = MaterialTheme.typography.bodySmall)
                        Text("Files: $filesCount", style = MaterialTheme.typography.bodySmall)
                        Text("Total size: ${MimeTypeUtil.formatFileSize(totalSize)}", style = MaterialTheme.typography.bodySmall)
                        selectedFiles.take(5).forEach { f ->
                            Text(
                                "${f.displayName} - ${if (f.isDirectory) "dir" else MimeTypeUtil.formatFileSize(f.size)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (selectedFiles.size > 5) Text("... and ${selectedFiles.size - 5} more", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSelectedInfoDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
}

@Composable
fun DownloadsDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    downloadStates: Map<String, FileDownloadState>,
    onCancelDownload: (String) -> Unit
) {
    val inProgress = downloadStates.values.filter { it.status == DownloadStatus.IN_PROGRESS }
    val completed = downloadStates.values.filter { it.status == DownloadStatus.COMPLETE }
    val failed = downloadStates.values.filter { it.status == DownloadStatus.FAILED }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Downloads") },
        text = {
            if (downloadStates.isEmpty()) {
                Text("No active downloads")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    if (completed.isNotEmpty()) {
                        Text("Completed (${completed.size})", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.size(4.dp))
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(inProgress, key = { it.path }) { state ->
                            DownloadItem(state = state, onCancel = { onCancelDownload(state.path) })
                        }
                        items(completed, key = { it.path }) { state ->
                            DownloadItem(state = state, onCancel = {})
                        }
                        items(failed, key = { it.path }) { state ->
                            DownloadItem(state = state, onCancel = { onCancelDownload(state.path) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("OK")
            }
        }
    )
}

@Composable
fun DownloadItem(
    state: FileDownloadState,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(state.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${MimeTypeUtil.formatFileSize(state.bytesDownloaded)} / ${MimeTypeUtil.formatFileSize(state.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when (state.status) {
            DownloadStatus.IN_PROGRESS -> {
                LinearProgressIndicator(
                    progress = { state.progressFraction },
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            else -> {}
        }
    }
}

@Composable
fun HostListContent(
    hosts: List<SavedHost>,
    onHostClick: (SavedHost) -> Unit,
    onDeleteHost: (String) -> Unit,
    onEditHost: (SavedHost) -> Unit,
    onAddHost: () -> Unit
) {
    if (hosts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("No hosts configured", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onAddHost) {
                    Text("Add Host")
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(hosts, key = { it.id }) { host ->
                HostCard(
                    host = host,
                    onClick = { onHostClick(host) },
                    onDelete = { onDeleteHost(host.id) },
                    onEdit = { onEditHost(host) }
                )
            }
        }
    }
}

@Composable
fun HostCard(
    host: SavedHost,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = host.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${host.host}:${host.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit host",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete host",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AddEditHostDialog(
    showDialog: Boolean,
    initialName: String = "",
    initialHost: String = "",
    initialPort: String = "9090",
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, host: String, port: Int) -> Unit
) {
    if (showDialog) {
        var nameText by rememberSaveable { mutableStateOf(initialName) }
        var hostText by rememberSaveable { mutableStateOf(initialHost) }
        var portText by rememberSaveable { mutableStateOf(initialPort) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(if (initialName.isNotEmpty()) "Edit Host" else "Add Host") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hostText,
                        onValueChange = { hostText = it },
                        label = { Text("Host (IP or hostname)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            if (it.isEmpty() || it.all { c -> c.isDigit() }) portText = it
                        },
                        label = { Text("Port") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val port = portText.toIntOrNull()
                        if (hostText.isNotBlank() && port != null) {
                            onConfirm(nameText.ifEmpty { hostText }, hostText, port)
                        }
                    }
                ) {
                    Text(if (initialName.isNotEmpty()) "Save" else "Add")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ConnectingContent(config: ConnectionConfig) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Connecting to ${config.host}:${config.port}...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun ErrorContent(
    message: String,
    attempt: Int,
    maxAttempts: Int,
    onRetry: () -> Unit,
    config: ConnectionConfig
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Connection failed: $message",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Server: ${config.host}:${config.port}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun GaveUpContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}


