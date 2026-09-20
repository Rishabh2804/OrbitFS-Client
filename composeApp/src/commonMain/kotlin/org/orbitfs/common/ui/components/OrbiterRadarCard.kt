package org.orbitfs.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(avatar.color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(avatar.icon, contentDescription = null, tint = avatar.color)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(orbiter.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${orbiter.host}:${orbiter.port}", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isAlreadySaved) {
                Icon(Icons.Rounded.Check, contentDescription = "Saved", tint = ColorStatusGreen)
            } else {
                IconButton(onClick = onClick) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
