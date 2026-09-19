package org.orbitfs.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.orbitfs.common.model.OrbiterInfo
import org.orbitfs.common.model.PilotAvatar
import org.orbitfs.common.ui.theme.ColorStatusGreen

@Composable
fun OrbiterRadarCard(
    orbiter: OrbiterInfo, 
    isAlreadySaved: Boolean,
    onClick: () -> Unit
) {
    val avatar = PilotAvatar.getById(orbiter.avatarId ?: "rocket")

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
                    imageVector = avatar.icon,
                    contentDescription = null,
                    tint = avatar.color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                val cleanName = orbiter.name.removePrefix("OrbitFS-")
                Text(cleanName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${orbiter.host}:${orbiter.port}",
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
