package org.orbitfs.common.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SpaceDark_Primary,
    onPrimary = SpaceDark_OnPrimary,
    secondary = SpaceDark_Secondary,
    tertiary = SpaceDark_Tertiary,
    background = SpaceDark_Background,
    surface = SpaceDark_Surface,
    surfaceVariant = SpaceDark_SurfaceVariant,
    outline = SpaceDark_Outline,
    error = ColorStatusRed
)

private val LightColorScheme = lightColorScheme(
    primary = SpaceLight_Primary,
    onPrimary = SpaceLight_OnPrimary,
    secondary = SpaceLight_Secondary,
    tertiary = SpaceLight_Tertiary,
    background = SpaceLight_Background,
    surface = SpaceLight_Surface,
    surfaceVariant = SpaceLight_SurfaceVariant,
    outline = SpaceLight_Outline,
    error = ColorStatusRed
)

@Composable
fun OrbitFSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
