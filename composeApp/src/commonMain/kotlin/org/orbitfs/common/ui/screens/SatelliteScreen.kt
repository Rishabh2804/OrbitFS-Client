package org.orbitfs.common.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.orbitfs.common.data.SavedHost
import org.orbitfs.common.model.OrbiterInfo
import org.orbitfs.common.model.SatelliteState
import org.orbitfs.common.ui.theme.ColorStatusGreen
import org.orbitfs.common.ui.components.OrbiterRadarCard
import org.orbitfs.common.ui.components.SatelliteConfigDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteScreen(
    state: SatelliteState,
    username: String,
    pilotAvatarId: String,
    onBack: () -> Unit,
    onToggleServer: () -> Unit,
    onPickFolder: () -> Unit,
    onUpdateConfig: (Int, String?) -> Unit,
    onPermissionStatusClick: () -> Unit = {}
) {
    var showConfigDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Satellite Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(username, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
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
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            StatusCard(
                isRunning = state.isRunning, 
                onToggle = onToggleServer
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Unified Configuration Button
            Button(
                onClick = { showConfigDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (state.isRunning) "Edit Configuration" else "Configure Satellite",
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Current root path info card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = state.rootPath ?: "Internal Storage (Default)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (state.isRunning) {
                Spacer(modifier = Modifier.height(24.dp))
                ConnectivityCard(port = state.port, token = state.pairingToken ?: "NO-TOKEN")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Hardware Permission Section
            PermissionGuardSection(onClick = onPermissionStatusClick)

            Spacer(modifier = Modifier.height(40.dp))
        }

        if (showConfigDialog) {
            SatelliteConfigDialog(
                initialPort = state.port,
                initialPath = state.rootUri,
                onDismiss = { showConfigDialog = false },
                onConfirm = { port, _ ->
                    onUpdateConfig(port, state.rootUri)
                    showConfigDialog = false
                },
                onPickFolder = onPickFolder
            )
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
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Router,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (isRunning) ColorStatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
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
                Icon(if (isRunning) Icons.Rounded.PowerSettingsNew else Icons.Rounded.RocketLaunch, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(if (isRunning) "Stop Satellite" else "Launch Satellite", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ConnectivityCard(port: Int, token: String) {
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
            
            DetailRow("Pairing Token", token)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow("Dynamic Port", port.toString())
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun PermissionGuardSection(onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.GppGood, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Hardware Permissions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Ensure 'All Files Access' and 'Notifications' are enabled for maximum stability.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Check / Request Permissions",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun RadarSection(
    discoveredOrbiters: Set<OrbiterInfo>,
    savedHosts: List<SavedHost>,
    onOrbiterClick: (OrbiterInfo) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Orbit Radar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (discoveredOrbiters.isEmpty()) {
            InfoNote("Scanning for nearby satellites...")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                discoveredOrbiters.forEach { orbiter ->
                    val isAlreadySaved = savedHosts.any { it.nodeId == orbiter.nodeId }
                    OrbiterRadarCard(orbiter = orbiter, isAlreadySaved = isAlreadySaved, onClick = { onOrbiterClick(orbiter) })
                }
            }
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
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
