package org.orbitfs.android.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("orbitfs_settings", Context.MODE_PRIVATE)

    // Global Preferences
    private val _username = MutableStateFlow(prefs.getString(KEY_USERNAME, "Pilot-${UUID.randomUUID().toString().takeLast(4)}")!!)
    val username: StateFlow<String> = _username

    private val _avatarId = MutableStateFlow(prefs.getString(KEY_AVATAR_ID, "rocket")!!)
    val avatarId: StateFlow<String> = _avatarId

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, "System")!!)
    val themeMode: StateFlow<String> = _themeMode

    private val _showHiddenFiles = MutableStateFlow(prefs.getBoolean(KEY_SHOW_HIDDEN, false))
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    private val _backgroundMonitorEnabled = MutableStateFlow(prefs.getBoolean(KEY_BACKGROUND_MONITOR, true))
    val backgroundMonitorEnabled: StateFlow<Boolean> = _backgroundMonitorEnabled

    fun updateUsername(name: String) {
        prefs.edit().putString(KEY_USERNAME, name).apply()
        _username.value = name
    }

    fun updateAvatarId(id: String) {
        prefs.edit().putString(KEY_AVATAR_ID, id).apply()
        _avatarId.value = id
    }

    fun updateThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    fun setShowHiddenFiles(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, show).apply()
        _showHiddenFiles.value = show
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        _notificationsEnabled.value = enabled
    }

    fun setBackgroundMonitorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_MONITOR, enabled).apply()
        _backgroundMonitorEnabled.value = enabled
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_AVATAR_ID = "avatar_id"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SHOW_HIDDEN = "show_hidden_files"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_BACKGROUND_MONITOR = "background_monitor_enabled"
    }
}
