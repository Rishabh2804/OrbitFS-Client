package org.orbitfs.android.ui.screens

import android.net.nsd.NsdServiceInfo
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.orbitfs.android.model.SatelliteState
import org.orbitfs.android.ui.components.SettingsGroup
import org.orbitfs.android.ui.components.SettingsRow
import org.orbitfs.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteScreen(
    state: SatelliteState,
    discoveredOrbiters: Set<NsdServiceInfo>,
    onBack: () -> Unit,
    onToggleServer: () -> Unit,
    onPickFolder: () -> Unit,
    onUpdatePort: (Int) -> Unit,
    onOrbiterClick: (NsdServiceInfo) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showPortDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Satellite Mode", fontWeight = FontWeight.Bold) },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Card
            StatusCard(
                isRunning = state.isRunning,
                onToggle = onToggleServer
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Configuration Group
            SettingsGroup(title = "Local Node Configuration", icon = Icons.Rounded.Settings) {
                SettingsRow(
                    title = "Shared Directory",
                    subtitle = state.rootPath ?: "No folder selected",
                    onClick = { if (!state.isRunning) onPickFolder() },
                    control = {
                        Icon(
                            Icons.Rounded.Folder, 
                            contentDescription = null, 
                            tint = if (state.rootPath != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    title = "Broadcast Port",
                    subtitle = "Current: ${state.port}",
                    onClick = if (!state.isRunning) ({ showPortDialog = true }) else null,
                    control = {
                        Text(
                            "${state.port}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (!state.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Connectivity Card
            if (state.isRunning) {
                ConnectivityCard(
                    port = state.port,
                    token = state.pairingToken ?: "NO-TOKEN",
                    onCopyToken = { clipboardManager.setText(AnnotatedString(state.pairingToken ?: "")) }
                )
            } else {
                InfoNote("Launch your satellite to allow other devices to browse files on this phone.")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Radar Section
            RadarSection(
                discoveredOrbiters = discoveredOrbiters,
                onOrbiterClick = onOrbiterClick
            )

            Spacer(modifier = Modifier.height(40.dp))
        }

        if (showPortDialog) {
            // Port input logic (simplified)
            onUpdatePort(9090)
            showPortDialog = false
        }
    }
}

@Composable
fun StatusCard(isRunning: Boolean, onToggle: () -> Unit) {
    Surface(
        color = if (isRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (isRunning) ColorStatusGreen.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Router,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (isRunning) ColorStatusGreen else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                if (isRunning) "Satellite is Online" else "Satellite is Offline",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (isRunning) ColorStatusGreen else MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                if (isRunning) "Broadcasting to local network" else "Ready to launch",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onToggle,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error 
                                    else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(if (isRunning) "Stop Satellite" else "Launch Satellite", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ConnectivityCard(port: Int, token: String, onCopyToken: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connection Details", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            DetailRow("Pairing Token", token, onAction = onCopyToken, actionIcon = Icons.Rounded.ContentCopy)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow("Dynamic Port", port.toString())
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, onAction: (() -> Unit)? = null, actionIcon: ImageVector? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        if (onAction != null && actionIcon != null) {
            IconButton(onClick = onAction) {
                Icon(actionIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun RadarSection(
    discoveredOrbiters: Set<NsdServiceInfo>,
    onOrbiterClick: (NsdServiceInfo) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Orbit Radar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (discoveredOrbiters.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Scanning for nearby satellites...",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                discoveredOrbiters.forEach { orbiter ->
                    OrbiterCard(orbiter = orbiter, onClick = { onOrbiterClick(orbiter) })
                }
            }
        }
    }
}

@Composable
fun OrbiterCard(orbiter: NsdServiceInfo, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                val cleanName = orbiter.serviceName.removePrefix("OrbitFS-")
                Text(cleanName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${orbiter.host?.hostAddress ?: "Resolving..."}:${orbiter.port}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Rounded.ContentCopy, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun InfoNote(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SatellitePreview() {
    OrbitFSTheme {
        SatelliteScreen(
            state = SatelliteState(isRunning = true, rootPath = "/sdcard/Downloads", pairingToken = "XRT-992"),
            discoveredOrbiters = emptySet(),
            onBack = {},
            onToggleServer = {},
            onPickFolder = {},
            onUpdatePort = {},
            onOrbiterClick = {}
        )
    }
}
