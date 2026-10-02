package org.orbitfs.android.ui.screens

import android.net.nsd.NsdServiceInfo
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.orbitfs.android.data.SavedHost
import org.orbitfs.android.model.PilotAvatar
import org.orbitfs.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    discoveredOrbiters: Set<NsdServiceInfo>,
    savedHosts: List<SavedHost>,
    pilotAvatarId: String,
    onBack: () -> Unit,
    onOrbiterClick: (NsdServiceInfo) -> Unit
) {
    val myAvatar = PilotAvatar.getById(pilotAvatarId)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orbit Radar", fontWeight = FontWeight.Bold) },
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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                RadarAnimation()
                
                Surface(
                    shape = CircleShape,
                    color = myAvatar.color.copy(alpha = 0.2f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = myAvatar.icon,
                            contentDescription = "Me",
                            tint = myAvatar.color,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Text(
                "Nearby Satellites",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).align(Alignment.Start)
            )
            
            Text(
                "Detecting active nodes in your local network orbit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (discoveredOrbiters.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Scanning frequency...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(discoveredOrbiters.toList(), key = { it.serviceName }) { orbiter ->
                        val isAlreadySaved = savedHosts.any { 
                            it.host == orbiter.host?.hostAddress && it.port == orbiter.port 
                        }
                        OrbiterRadarCard(
                            orbiter = orbiter, 
                            isAlreadySaved = isAlreadySaved,
                            onClick = { onOrbiterClick(orbiter) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RadarAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val radius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Radius"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha"
    )

    val color = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.size(280.dp)) {
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = radius * size.maxDimension / 2,
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Static circles
        drawCircle(color = color.copy(alpha = 0.05f), radius = size.maxDimension / 2, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = color.copy(alpha = 0.05f), radius = size.maxDimension / 3, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = color.copy(alpha = 0.05f), radius = size.maxDimension / 6, style = Stroke(width = 1.dp.toPx()))
    }
}

@Composable
fun OrbiterRadarCard(
    orbiter: NsdServiceInfo, 
    isAlreadySaved: Boolean,
    onClick: () -> Unit
) {
    // Extract avatarId from TXT records if available
    val avatarIdBytes = orbiter.attributes?.get("avatarId")
    val avatarId = avatarIdBytes?.let { String(it) } ?: "rocket"
    val avatar = PilotAvatar.getById(avatarId)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(avatar.color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (avatarIdBytes != null) avatar.icon else Icons.Rounded.Router,
                    contentDescription = null,
                    tint = avatar.color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                val cleanName = orbiter.serviceName.removePrefix("OrbitFS-")
                Text(cleanName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${orbiter.host?.hostAddress ?: "Resolving..."}:${orbiter.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isAlreadySaved) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ColorStatusGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Saved",
                        tint = ColorStatusGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                IconButton(onClick = onClick) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Add Node",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RadarPreview() {
    OrbitFSTheme {
        RadarScreen(
            discoveredOrbiters = emptySet(),
            savedHosts = emptyList(),
            pilotAvatarId = "rocket",
            onBack = {},
            onOrbiterClick = {}
        )
    }
}
