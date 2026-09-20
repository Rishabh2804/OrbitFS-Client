package org.orbitfs.common.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.orbitfs.common.data.SavedHost
import org.orbitfs.common.ui.components.NumberInputDialog
import org.orbitfs.common.ui.components.SettingsGroup
import org.orbitfs.common.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSettingsPage(
    currentHost: SavedHost?,
    onUpdateSettings: (Boolean, Int, Int, Int) -> Unit,
    onRemoveHost: (String) -> Unit,
    onBack: () -> Unit
) {
    val host = currentHost ?: return

    var isGuarded by remember { mutableStateOf(host.guardedDeletion) }
    var currentTimeout by remember { mutableIntStateOf(host.socketTimeoutMs) }
    var currentChunkSize by remember { mutableIntStateOf(host.chunkSizeKb) }
    var currentAutoLoad by remember { mutableIntStateOf(host.autoLoadLimitKb) }

    var showThresholdDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showChunkSizeDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsGroup(title = "Safety", icon = Icons.Rounded.Settings) {
                SettingsRow(
                    title = "Guarded Deletion",
                    subtitle = "Always prompt before deleting items",
                    onClick = { 
                        isGuarded = !isGuarded
                        onUpdateSettings(isGuarded, currentTimeout, currentChunkSize, currentAutoLoad)
                    },
                    control = {
                        Switch(
                            checked = isGuarded,
                            onCheckedChange = { 
                                isGuarded = it
                                onUpdateSettings(isGuarded, currentTimeout, currentChunkSize, currentAutoLoad)
                            }
                        )
                    }
                )
            }

            SettingsGroup(title = "Network Tuning", icon = Icons.Rounded.Settings) {
                SettingsRow(
                    title = "Socket Timeout",
                    subtitle = "Connection drop threshold",
                    onClick = { showTimeoutDialog = true },
                    control = { Text("${currentTimeout}ms", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    title = "Chunk Buffer Size",
                    subtitle = "Memory per frame",
                    onClick = { showChunkSizeDialog = true },
                    control = { Text("$currentChunkSize KB", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    title = "Auto-load Limit",
                    subtitle = "Pre-load small files",
                    onClick = { showThresholdDialog = true },
                    control = { Text("${currentAutoLoad}KB", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Delete Node Connection", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (showThresholdDialog) {
            NumberInputDialog(
                title = "Auto-load Limit",
                initialValue = currentAutoLoad.toString(),
                unit = "KB",
                onDismiss = { showThresholdDialog = false },
                onConfirm = {
                    it.toIntOrNull()?.let { valKb -> 
                        currentAutoLoad = valKb
                        onUpdateSettings(isGuarded, currentTimeout, currentChunkSize, currentAutoLoad)
                    }
                    showThresholdDialog = false
                }
            )
        }

        if (showTimeoutDialog) {
            NumberInputDialog(
                title = "Socket Timeout",
                initialValue = currentTimeout.toString(),
                unit = "ms",
                onDismiss = { showTimeoutDialog = false },
                onConfirm = {
                    it.toIntOrNull()?.let { valMs -> 
                        currentTimeout = valMs
                        onUpdateSettings(isGuarded, currentTimeout, currentChunkSize, currentAutoLoad)
                    }
                    showTimeoutDialog = false
                }
            )
        }

        if (showChunkSizeDialog) {
            NumberInputDialog(
                title = "Chunk Size",
                initialValue = currentChunkSize.toString(),
                unit = "KB",
                onDismiss = { showChunkSizeDialog = false },
                onConfirm = {
                    it.toIntOrNull()?.let { valKb -> 
                        currentChunkSize = valKb
                        onUpdateSettings(isGuarded, currentTimeout, currentChunkSize, currentAutoLoad)
                    }
                    showChunkSizeDialog = false
                }
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Node") },
                text = { Text("Are you sure you want to remove this node from your saved list? This will also disconnect the current session.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onRemoveHost(host.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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
    }
}
