package org.orbitfs.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.orbitfs.android.data.SavedHost
import org.orbitfs.android.model.PilotAvatar
import org.orbitfs.android.model.SatelliteState
import org.orbitfs.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeHubScreen(
    hosts: List<SavedHost>,
    pingResults: Map<String, Int?>,
    username: String,
    avatarId: String = "rocket",
    satelliteState: SatelliteState = SatelliteState(),
    onAddNode: () -> Unit,
    onEditNode: (SavedHost) -> Unit,
    onConnect: (SavedHost) -> Unit,
    onRetry: (SavedHost) -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLaunchSatellite: () -> Unit
) {
    val avatar = PilotAvatar.getById(avatarId)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "OrbitFS",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            username,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onProfileClick) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = avatar.color.copy(alpha = 0.2f)
                        ) {
                            Icon(
                                imageVector = avatar.icon,
                                contentDescription = "Profile",
                                tint = avatar.color,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddNode,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add Node") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "Nodes",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                "Select a server to browse your files",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            // Local Satellite Card
            SatelliteLauncherCard(
                state = satelliteState,
                onClick = onLaunchSatellite
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            if (hosts.isEmpty() && !satelliteState.isRunning) {
                EmptyNodesContent()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(hosts, key = { it.id }) { host ->
                        val ping = pingResults[host.id]
                        NodeCard(
                            host = host,
                            ping = ping,
                            onConnect = { onConnect(host) },
                            onRetry = { onRetry(host) },
                            onEdit = { onEditNode(host) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SatelliteLauncherCard(
    state: SatelliteState,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                           else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (state.isRunning) ColorStatusGreen.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = if (state.isRunning) ColorStatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Local Satellite",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (state.isRunning) "Running on port ${state.port}" else "Turn this device into a node",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.isRunning) {
                StatusPill(ping = 0) // Local is always fast
            } else {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
fun NodeCard(
    host: SavedHost,
    ping: Int?,
    onConnect: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit
) {
    val isOnline = ping != null

    Card(
        onClick = if (isOnline) onConnect else onRetry,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Dns,
                    contentDescription = null,
                    tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    host.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${host.host}:${host.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isOnline) {
                    StatusPill(ping = ping)
                } else {
                    Text(
                        "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = { onEdit() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusPill(ping: Int?) {
    Surface(
        color = ColorStatusGreen.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ColorStatusGreen)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "${ping}ms",
                style = MaterialTheme.typography.labelSmall,
                color = ColorStatusGreen,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyNodesContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Dns,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No nodes saved yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NodeHubPreview() {
    OrbitFSTheme {
        NodeHubScreen(
            hosts = listOf(
                SavedHost("1", "Production Cloud", "orbitfs.prod.io", 443),
                SavedHost("2", "Home Lab", "192.168.1.100", 9090)
            ),
            pingResults = mapOf("1" to 42, "2" to null),
            username = "Pilot-Alpha",
            avatarId = "rocket",
            satelliteState = SatelliteState(),
            onAddNode = {},
            onEditNode = {},
            onConnect = {},
            onRetry = {},
            onProfileClick = {},
            onSettingsClick = {},
            onLaunchSatellite = {}
        )
    }
}
