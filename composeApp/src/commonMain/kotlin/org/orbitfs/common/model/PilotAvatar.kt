package org.orbitfs.common.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class PilotAvatar(
    val id: String,
    val icon: ImageVector,
    val color: Color,
    val name: String
) {
    companion object {
        val ALL = listOf(
            PilotAvatar("rocket", Icons.Rounded.Rocket, Color(0xFFBB86FC), "Rocket"),
            PilotAvatar("star", Icons.Rounded.Star, Color(0xFFFFD600), "Star"),
            PilotAvatar("moon", Icons.Rounded.ModeNight, Color(0xFF90CAF9), "Moon"),
            PilotAvatar("diamond", Icons.Rounded.Diamond, Color(0xFF00E5FF), "Diamond"),
            PilotAvatar("flare", Icons.Rounded.Flare, Color(0xFFFF5252), "Flare"),
            PilotAvatar("awesome", Icons.Rounded.AutoAwesome, Color(0xFFE040FB), "Awesome"),
            PilotAvatar("cloud", Icons.Rounded.Cloud, Color(0xFFB0BEC5), "Cloud"),
            PilotAvatar("adb", Icons.Rounded.Adb, Color(0xFF7CB342), "Android"),
            PilotAvatar("public", Icons.Rounded.Public, Color(0xFF4CAF50), "Public"),
            PilotAvatar("face", Icons.Rounded.Face, Color(0xFFFFAB40), "Face"),
            PilotAvatar("language", Icons.Rounded.Language, Color(0xFF2196F3), "Web"),
            PilotAvatar("ticket", Icons.Rounded.AirplaneTicket, Color(0xFF795548), "Ticket")
        )
        
        fun getById(id: String): PilotAvatar = ALL.find { it.id == id } ?: ALL.first()
    }
}
