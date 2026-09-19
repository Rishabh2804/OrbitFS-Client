package org.orbitfs.common.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.orbitfs.common.util.PlatformContext
import java.util.UUID
import androidx.core.content.edit

actual class SettingsRepository actual constructor(context: PlatformContext) {
    private val androidContext = context.context
    private val prefs = androidContext.getSharedPreferences("orbitfs_settings", Context.MODE_PRIVATE)

    private val _nodeId = MutableStateFlow(getOrCreateNodeId())
    actual val nodeId: StateFlow<String> = _nodeId

    private val _username = MutableStateFlow(prefs.getString("username", "Pilot") ?: "Pilot")
    actual val username: StateFlow<String> = _username

    private val _avatarId = MutableStateFlow(prefs.getString("avatar_id", "rocket") ?: "rocket")
    actual val avatarId: StateFlow<String> = _avatarId

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "System Default") ?: "System Default")
    actual val themeMode: StateFlow<String> = _themeMode

    private val _satelliteEnabled = MutableStateFlow(prefs.getBoolean("satellite_enabled", false))
    actual val satelliteEnabled: StateFlow<Boolean> = _satelliteEnabled

    private val _satellitePort = MutableStateFlow(prefs.getInt("satellite_port", 9090))
    actual val satellitePort: StateFlow<Int> = _satellitePort

    private val _satelliteRootUri = MutableStateFlow(prefs.getString("satellite_root_uri", null))
    actual val satelliteRootUri: StateFlow<String?> = _satelliteRootUri

    private val _showHiddenFiles = MutableStateFlow(prefs.getBoolean("show_hidden_files", false))
    actual val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean("notifications_enabled", true))
    actual val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    private fun getOrCreateNodeId(): String {
        var id = prefs.getString("node_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit { putString("node_id", id) }
        }
        return id
    }

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            "username" -> _username.value = p.getString(key, "Pilot") ?: "Pilot"
            "avatar_id" -> _avatarId.value = p.getString(key, "rocket") ?: "rocket"
            "theme_mode" -> _themeMode.value = p.getString(key, "System Default") ?: "System Default"
            "satellite_enabled" -> _satelliteEnabled.value = p.getBoolean(key, false)
            "satellite_port" -> _satellitePort.value = p.getInt(key, 9090)
            "satellite_root_uri" -> _satelliteRootUri.value = p.getString(key, null)
            "show_hidden_files" -> _showHiddenFiles.value = p.getBoolean(key, false)
            "notifications_enabled" -> _notificationsEnabled.value = p.getBoolean(key, true)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    actual fun setUsername(name: String) {
        _username.value = name
        prefs.edit { putString("username", name) }
    }

    actual fun setAvatarId(id: String) {
        _avatarId.value = id
        prefs.edit { putString("avatar_id", id) }
    }

    actual fun updateThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit { putString("theme_mode", mode) }
    }

    actual fun setSatelliteEnabled(enabled: Boolean) {
        _satelliteEnabled.value = enabled
        prefs.edit { putBoolean("satellite_enabled", enabled) }
    }

    actual fun setSatellitePort(port: Int) {
        _satellitePort.value = port
        prefs.edit { putInt("satellite_port", port) }
    }

    actual fun setSatelliteRootUri(uri: String?) {
        _satelliteRootUri.value = uri
        prefs.edit { putString("satellite_root_uri", uri) }
    }

    actual fun setShowHiddenFiles(show: Boolean) {
        _showHiddenFiles.value = show
        prefs.edit { putBoolean("show_hidden_files", show) }
    }

    actual fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit { putBoolean("notifications_enabled", enabled) }
    }
}
