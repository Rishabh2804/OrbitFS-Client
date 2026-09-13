package org.orbitfs.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.orbitfs.android.data.SettingsRepository
import org.orbitfs.android.model.PilotAvatar
import org.orbitfs.android.ui.components.TextInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val username by settingsRepository.username.collectAsState()
    val avatarId by settingsRepository.avatarId.collectAsState()
    
    var showUsernameDialog by remember { mutableStateOf(false) }
    val currentAvatar = PilotAvatar.getById(avatarId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pilot Identity", fontWeight = FontWeight.Bold) },
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
            // Selected Avatar Display
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(currentAvatar.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = currentAvatar.icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = currentAvatar.color
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Surface(
                onClick = { showUsernameDialog = true },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pilot Name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                "Choose Your Emblem",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Emblem Selection Grid
            // Note: LazyVerticalGrid inside a verticalScroll Column needs a fixed height or to be non-lazy if list is small.
            // Since it's only 12 items, we can use a simpler layout or just give it height.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val chunks = PilotAvatar.ALL.chunked(4)
                chunks.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { avatar ->
                            val isSelected = avatar.id == avatarId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) avatar.color.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    )
                                    .clickable { settingsRepository.updateAvatarId(avatar.id) }
                                    .then(
                                        if (isSelected) Modifier.background(avatar.color.copy(alpha = 0.1f))
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = avatar.icon,
                                    contentDescription = avatar.name,
                                    tint = if (isSelected) avatar.color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(32.dp)
                                )
                                
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(avatar.color.copy(alpha = 0.05f))
                                    )
                                }
                            }
                        }
                        // Fill empty spots if last row isn't full
                        repeat(4 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                "Your identity is shared with satellites to distinguish between different pilots connected to the same node.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        
        if (showUsernameDialog) {
            TextInputDialog(
                title = "Update Pilot Name",
                initialValue = username,
                onDismiss = { showUsernameDialog = false },
                onConfirm = {
                    if (it.isNotBlank()) settingsRepository.updateUsername(it)
                    showUsernameDialog = false
                }
            )
        }
    }
}
