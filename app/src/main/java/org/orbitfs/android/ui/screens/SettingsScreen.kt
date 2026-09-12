package org.orbitfs.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.orbitfs.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onClearCache: () -> Unit
) {
    var guardedDeletion by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            SettingsGroup(title = "Network") {
                SettingsRow(
                    title = "Socket Timeout",
                    subtitle = "Time before connection drop",
                    control = {
                        Text(
                            "5000ms",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    title = "Chunk Size",
                    subtitle = "Buffer size per frame",
                    control = {
                        Text(
                            "256 KB",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }

            SettingsGroup(title = "Storage") {
                SettingsRow(
                    title = "Memory Cache",
                    subtitle = "LRU file chunk limit",
                    control = {
                        Text(
                            "512 MB",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    title = "Cache usage",
                    subtitle = "Currently using 128 MB",
                    control = {
                        TextButton(onClick = onClearCache) {
                            Text("Clear")
                        }
                    }
                )
            }

            SettingsGroup(title = "Safety") {
                SettingsRow(
                    title = "Confirm deletion",
                    subtitle = "Require prompts for delete",
                    control = {
                        Switch(
                            checked = guardedDeletion,
                            onCheckedChange = { guardedDeletion = it }
                        )
                    }
                )
            }

            SettingsGroup(title = "About") {
                SettingsRow(title = "Version", subtitle = "App version", control = { Text("0.1.0") })
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(title = "Protocol", subtitle = "OrbitFS version", control = { Text("v1.4.0") })
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
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
fun SettingsRow(title: String, subtitle: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
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

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    OrbitFSTheme {
        SettingsScreen(
            onBack = {},
            onClearCache = {}
        )
    }
}
