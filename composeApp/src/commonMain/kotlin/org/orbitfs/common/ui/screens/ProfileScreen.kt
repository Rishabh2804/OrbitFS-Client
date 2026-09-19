package org.orbitfs.common.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.orbitfs.common.model.PilotAvatar
import org.orbitfs.common.data.SettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String,
    avatarId: String,
    onBack: () -> Unit,
    onUpdateUsername: (String) -> Unit,
    onUpdateAvatarId: (String) -> Unit,
    settingsRepository: SettingsRepository? = null
) {
    var tempUsername by remember(username) { mutableStateOf(username) }
    var tempAvatarId by remember(avatarId) { mutableStateOf(avatarId) }
    
    val currentAvatar = PilotAvatar.getById(tempAvatarId)
    val hasChanges = tempUsername != username || tempAvatarId != avatarId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pilot Identity", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasChanges) {
                        Button(
                            onClick = {
                                if (tempUsername.isNotBlank()) onUpdateUsername(tempUsername)
                                onUpdateAvatarId(tempAvatarId)
                            },
                            modifier = Modifier.padding(end = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("SAVE")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (hasChanges) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (tempUsername.isNotBlank()) onUpdateUsername(tempUsername)
                        onUpdateAvatarId(tempAvatarId)
                    },
                    icon = { Icon(Icons.Rounded.Save, contentDescription = null) },
                    text = { Text("Apply Changes") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Preview
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(currentAvatar.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = currentAvatar.icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = currentAvatar.color
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = tempUsername,
                onValueChange = { tempUsername = it },
                label = { Text("Pilot Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Text(
                "Choose Your Emblem",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val chunks = PilotAvatar.ALL.chunked(4)
                chunks.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rowItems.forEach { avatar ->
                            val isSelected = avatar.id == tempAvatarId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) avatar.color.copy(alpha = 0.3f) 
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    )
                                    .clickable { tempAvatarId = avatar.id },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = avatar.icon,
                                    contentDescription = avatar.name,
                                    tint = if (isSelected) avatar.color 
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(32.dp)
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        contentAlignment = Alignment.TopEnd
                                    ) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = avatar.color,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp)) // Extra space for FAB
        }
    }
}
