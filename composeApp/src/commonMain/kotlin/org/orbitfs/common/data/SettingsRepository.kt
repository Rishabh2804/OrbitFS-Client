package org.orbitfs.common.data

import kotlinx.coroutines.flow.StateFlow
import org.orbitfs.common.util.PlatformContext

expect class SettingsRepository(context: PlatformContext) {
    val nodeId: StateFlow<String>
    val username: StateFlow<String>
    val avatarId: StateFlow<String>
    val themeMode: StateFlow<String>
    val satelliteEnabled: StateFlow<Boolean>
    val satellitePort: StateFlow<Int>
    val satelliteRootUri: StateFlow<String?>
    val satelliteRootName: StateFlow<String?>
    val showHiddenFiles: StateFlow<Boolean>
    val notificationsEnabled: StateFlow<Boolean>

    fun setUsername(name: String)
    fun setAvatarId(id: String)
    fun updateThemeMode(mode: String)
    fun setSatelliteEnabled(enabled: Boolean)
    fun setSatellitePort(port: Int)
    fun setSatelliteRootUri(uri: String?, name: String? = null)
    fun setShowHiddenFiles(show: Boolean)
    fun setNotificationsEnabled(enabled: Boolean)
    fun resetIdentity()
}
