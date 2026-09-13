package org.orbitfs.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.orbitfs.android.data.SettingsRepository
import org.orbitfs.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onClearCache: () -> Unit
) {
    val themeMode by settingsRepository.themeMode.collectAsState()
    val notificationsEnabled by settingsRepository.notificationsEnabled.collectAsState()
    val backgroundMonitorEnabled by settingsRepository.backgroundMonitorEnabled.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Preferences", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // APPEARANCE
            SettingsGroup(title = "Appearance", icon = Icons.Rounded.Palette) {
                SettingsRow(
                    title = "App Theme",
                    subtitle = "Current: $themeMode",
                    onClick = { showThemeDialog = true },
                    control = {
                        Text(themeMode, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                )
            }

            // SYNC & TASKS
            SettingsGroup(title = "Operations", icon = Icons.Rounded.Sync) {
                SettingsRow(
                    title = "Background Monitor",
                    subtitle = "Keep node health active in background",
                    onClick = { settingsRepository.setBackgroundMonitorEnabled(!backgroundMonitorEnabled) },
                    control = { 
                        Switch(
                            checked = backgroundMonitorEnabled, 
                            onCheckedChange = null // Let the row handle it
                        ) 
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    title = "Notifications",
                    subtitle = "Alert on transfer completion",
                    onClick = { settingsRepository.setNotificationsEnabled(!notificationsEnabled) },
                    control = { 
                        Switch(
                            checked = notificationsEnabled, 
                            onCheckedChange = null // Let the row handle it
                        ) 
                    }
                )
            }

            // MAINTENANCE
            SettingsGroup(title = "Maintenance", icon = Icons.Rounded.DeleteSweep) {
                SettingsRow(
                    title = "Purge Local Data",
                    subtitle = "Clear all locally cached file chunks",
                    onClick = onClearCache,
                    control = {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                )
            }

            // ABOUT
            SettingsGroup(title = "About", icon = Icons.Rounded.Info) {
                SettingsRow(
                    title = "Version",
                    subtitle = "v0.1.0-alpha (Milestone 2)",
                    control = { Text("Latest", style = MaterialTheme.typography.labelSmall, color = ColorStatusGreen) }
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }

        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentTheme = themeMode,
                onDismiss = { showThemeDialog = false },
                onSelect = { 
                    settingsRepository.updateThemeMode(it)
                    showThemeDialog = false
                }
            )
        }
    }
}

@Composable
fun ThemeSelectionDialog(currentTheme: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val options = listOf("System", "Dark Space", "Light Orbit", "Dynamic")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == currentTheme, onClick = { onSelect(option) })
                        Spacer(Modifier.width(12.dp))
                        Text(option)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SettingsGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                content = content
            )
        }
    }
}

@Composable
fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit = {}, control: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            control()
        }
    }
}
