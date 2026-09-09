package org.orbitfs.android.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.orbitfs.android.R
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.client.ConnectionState
import org.orbitfs.android.data.ConnectionConfig
import org.orbitfs.android.data.HostRepository
import org.orbitfs.android.data.SavedHost
import org.orbitfs.android.model.FileInfo

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OrbitFSRoot(
    viewModel: FileBrowserViewModel,
    connectionManager: ConnectionManager,
    hostRepository: HostRepository
) {
    val connectionState by connectionManager.connectionState.collectAsStateWithLifecycle()
    val hosts by hostRepository.hosts.collectAsStateWithLifecycle()

    var showAddHostDialog by rememberSaveable { mutableStateOf(false) }
    var editingHost by rememberSaveable { mutableStateOf<SavedHost?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val currentPath = viewModel.currentPath
    val isHostView = connectionState !is ConnectionState.Connected

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isHostView) "OrbitFS" else (currentPath.ifEmpty { "Root" }),
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
                    } else {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
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
                            viewModel.deleteHost(id)
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
                    val context = LocalContext.current
                    FileBrowserContent(
                        state = viewModel.state.value,
                        onNavigate = { path -> viewModel.navigateTo(path) },
                        onOpenFile = { fileInfo -> viewModel.openFile(fileInfo, context) },
                        onRefresh = { viewModel.refreshCurrentPath() }
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

    if (showMenu) {
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Refresh") },
                onClick = {
                    showMenu = false
                    viewModel.refreshCurrentPath()
                }
            )
            DropdownMenuItem(
                text = { Text("Disconnect") },
                onClick = {
                    showMenu = false
                    viewModel.disconnect()
                }
            )
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
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) portText = it },
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
