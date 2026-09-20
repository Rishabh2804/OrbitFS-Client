package org.orbitfs.common.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.orbitfs.common.ui.components.SettingsGroup
import org.orbitfs.common.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: String,
    onBack: () -> Unit,
    onUpdateTheme: (String) -> Unit,
    onResetIdentity: () -> Unit = {},
    onCheckPermissions: () -> Unit = {}
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Preferences", fontWeight = FontWeight.Bold) },
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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsGroup(title = "Appearance", icon = Icons.Rounded.Palette) {
                SettingsRow(
                    title = "App Theme",
                    subtitle = "Current: $themeMode",
                    onClick = { showThemeDialog = true },
                    control = {
                        Text(themeMode, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                )
            }

            SettingsGroup(title = "Hardware & System", icon = Icons.Rounded.SettingsInputComponent) {
                SettingsRow(
                    title = "System Permissions",
                    subtitle = "Request File Access & Notifications",
                    onClick = onCheckPermissions,
                    control = {
                        Icon(Icons.Rounded.GppGood, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }

            SettingsGroup(title = "Identity Management", icon = Icons.Rounded.Fingerprint) {
                SettingsRow(
                    title = "Reset Pilot Identity",
                    subtitle = "Generates new Node ID and Name",
                    onClick = { showResetConfirm = true },
                    control = {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                )
            }

            SettingsGroup(title = "About", icon = Icons.Rounded.Info) {
                SettingsRow(
                    title = "Version",
                    subtitle = "v1.0.0-Orbit (KMP)",
                    control = { Text("Latest", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentMode = themeMode,
                onDismiss = { showThemeDialog = false },
                onSelect = { 
                    onUpdateTheme(it)
                    showThemeDialog = false
                }
            )
        }

        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("Reset Identity") },
                text = { Text("This will permanently clear your Pilot Name and generate a brand new Unique Node ID. You will need to re-pair with existing nodes.") },
                confirmButton = {
                    Button(
                        onClick = {
                            onResetIdentity()
                            showResetConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset & Wipe")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    currentMode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val modes = listOf("System Default", "Light Orbit", "Dark Space")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme") },
        text = {
            Column(Modifier.selectableGroup()) {
                modes.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (mode == currentMode),
                                onClick = { onSelect(mode) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (mode == currentMode),
                            onClick = null
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = mode,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
