package org.orbitfs.common.ui.screens

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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.orbitfs.common.data.SavedHost
import org.orbitfs.common.model.OrbiterInfo
import org.orbitfs.common.model.PilotAvatar
import org.orbitfs.common.ui.theme.ColorStatusGreen
import org.orbitfs.common.ui.theme.OrbitFSTheme
import org.orbitfs.common.ui.components.OrbiterRadarCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    discoveredOrbiters: Set<OrbiterInfo>,
    savedHosts: List<SavedHost>,
    pilotAvatarId: String,
    onBack: () -> Unit,
    onOrbiterClick: (OrbiterInfo) -> Unit,
    onRefresh: () -> Unit = {}
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
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
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
                    .height(280.dp),
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
                    items(discoveredOrbiters.toList(), key = { it.nodeId }) { orbiter ->
                        val isAlreadySaved = savedHosts.any { it.nodeId == orbiter.nodeId }
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

    Canvas(modifier = Modifier.size(240.dp)) {
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

@Preview
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
