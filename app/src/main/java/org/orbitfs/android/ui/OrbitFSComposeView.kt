package org.orbitfs.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.orbitfs.android.R
import org.orbitfs.android.client.ConnectionState
import org.orbitfs.android.model.FileInfo

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OrbitFSApp(
    state: BrowserState,
    onConnectClicked: () -> Unit,
    onDisconnectClicked: () -> Unit,
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit,
    onShowSettings: () -> Unit
) {
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_bar_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.pull_to_refresh)
                            )
                        }
                        IconButton(onClick = onShowSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val connectionState = state.connectionState
                when (connectionState) {
                    is ConnectionState.Disconnected -> {
                        DisconnectedContent(
                            onConnectClicked = onConnectClicked,
                            config = state.config
                        )
                    }
                    is ConnectionState.Connecting -> {
                        ConnectingContent(config = connectionState.config)
                    }
                    is ConnectionState.Connected -> {
                        FileBrowserContent(
                            state = state,
                            onNavigate = onNavigate,
                            onShowSettings = onShowSettings
                        )
                    }
                    is ConnectionState.Error -> {
                        ErrorContent(
                            message = connectionState.message,
                            attempt = connectionState.attempt,
                            maxAttempts = connectionState.maxAttempts,
                            onRetry = onConnectClicked,
                            onShowSettings = onShowSettings,
                            config = connectionState.config
                        )
                    }
                    is ConnectionState.GaveUp -> {
                        GaveUpContent(
                            message = connectionState.message,
                            onShowSettings = onShowSettings,
                            config = connectionState.config
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisconnectedContent(
    onConnectClicked: () -> Unit,
    config: org.orbitfs.android.data.ConnectionConfig
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "OrbitFS Client",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Server: ${config.host}:${config.port}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onConnectClicked) {
            Text(stringResource(R.string.connect_button))
        }
    }
}

@Composable
private fun ConnectingContent(config: org.orbitfs.android.data.ConnectionConfig) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Connecting to ${config.host}:${config.port}...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    attempt: Int,
    maxAttempts: Int,
    onRetry: () -> Unit,
    onShowSettings: () -> Unit,
    config: org.orbitfs.android.data.ConnectionConfig
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.server_unreachable),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Attempt $attempt of $maxAttempts",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Server: ${config.host}:${config.port}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onShowSettings) {
                Text("Settings")
            }
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry_button))
            }
        }
    }
}

@Composable
private fun GaveUpContent(
    message: String,
    onShowSettings: () -> Unit,
    config: org.orbitfs.android.data.ConnectionConfig
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Server: ${config.host}:${config.port}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onShowSettings) {
            Text("Settings")
        }
    }
}

@Composable
fun FileBrowserContent(
    state: BrowserState,
    onNavigate: (String) -> Unit,
    onShowSettings: () -> Unit
) {
    if (state.isLoading && state.files.isEmpty()) {
        LoadingContent()
    } else if (state.error != null && state.files.isEmpty()) {
        ErrorStateContent(
            error = state.error!!,
        )
    } else {
        FileListContent(
            files = state.files,
            path = state.currentPath,
            onNavigate = onNavigate,
            onShowSettings = onShowSettings
        )
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
private fun ErrorStateContent(
    error: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
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
    onNavigate: (String) -> Unit,
    onShowSettings: () -> Unit
) {
    if (files.isEmpty()) {
        EmptyContent()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (path != "/") {
                item {
                    FileRow(
                        file = FileInfo(
                            name = "..",
                            path = "..",
                            size = 0,
                            isDirectory = true,
                            lastModified = 0
                        ),
                        onClick = { onNavigate("..") }
                    )
                }
            }
            items(files, key = { it.path }) { file ->
                FileRow(
                    file = file,
                    onClick = {
                        if (file.isDirectory) {
                            onNavigate(file.path)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.empty_directory),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun FileRow(
    file: FileInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clickable(onClick = onClick)
            .focusable(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.size(8.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!file.isDirectory) {
                    Text(
                        text = formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionSettingsDialog(
    showDialog: Boolean,
    currentHost: String,
    currentPort: String,
    onDismissRequest: () -> Unit,
    onConfirm: (host: String, port: Int) -> Unit
) {
    if (showDialog) {
        var hostText by rememberSaveable { mutableStateOf(currentHost) }
        var portText by rememberSaveable { mutableStateOf(currentPort) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(R.string.connection_settings_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = hostText,
                        onValueChange = { hostText = it },
                        label = { Text(stringResource(R.string.server_host_label)) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            if (it.all { c -> c.isDigit() }) {
                                portText = it
                            }
                        },
                        label = { Text(stringResource(R.string.server_port_label)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val port = portText.toIntOrNull() ?: 9090
                        onConfirm(hostText, port)
                    }
                ) {
                    Text(stringResource(R.string.connect_button))
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

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
        size < 1024 * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024.0))
        else -> "%.1f GB".format(size / (1024.0 * 1024.0 * 1024.0))
    }
}
