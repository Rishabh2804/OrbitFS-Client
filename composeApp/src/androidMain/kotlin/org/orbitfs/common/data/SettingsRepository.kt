package org.orbitfs.common.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.orbitfs.common.util.PlatformContext
import org.orbitfs.common.util.IdentityGenerator
import java.io.File

actual class SettingsRepository actual constructor(context: PlatformContext) {
    private val androidContext = context.context
    private val prefs = androidContext.getSharedPreferences("orbitfs_settings", Context.MODE_PRIVATE)

    actual val nodeId: StateFlow<String> = _nodeId
    actual val username: StateFlow<String> = _username
    actual val avatarId: StateFlow<String> = _avatarId
    actual val themeMode: StateFlow<String> = _themeMode
    actual val satelliteEnabled: StateFlow<Boolean> = _satelliteEnabled
    actual val satellitePort: StateFlow<Int> = _satellitePort
    actual val satelliteRootUri: StateFlow<String?> = _satelliteRootUri
    actual val satelliteRootName: StateFlow<String?> = _satelliteRootName
    actual val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles
    actual val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    init {
        loadFromPrefs()
        prefs.registerOnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "username" -> _username.value = p.getString(key, "Pilot") ?: "Pilot"
                "avatar_id" -> _avatarId.value = p.getString(key, "rocket") ?: "rocket"
                "theme_mode" -> _themeMode.value = p.getString(key, "System Default") ?: "System Default"
                "satellite_port" -> _satellitePort.value = p.getInt(key, 9090)
                "satellite_root_uri" -> _satelliteRootUri.value = p.getString(key, null)
                "satellite_root_name" -> _satelliteRootName.value = p.getString(key, null)
                "show_hidden_files" -> _showHiddenFiles.value = p.getBoolean(key, false)
                "notifications_enabled" -> _notificationsEnabled.value = p.getBoolean(key, true)
                "satellite_enabled" -> _satelliteEnabled.value = p.getBoolean(key, false)
            }
        }
    }

    private fun loadFromPrefs() {
        if (!_initialized) {
            var id = prefs.getString("node_id", null)
            if (id.isNullOrBlank()) {
                id = IdentityGenerator.generateNodeId()
                prefs.edit { putString("node_id", id) }
            }
            _nodeId.value = id

            var name = prefs.getString("username", null)
            if (name.isNullOrBlank() || name == "Pilot") {
                name = IdentityGenerator.generateRandomName()
                prefs.edit { putString("username", name) }
            }
            _username.value = name

            var avatar = prefs.getString("avatar_id", null)
            if (avatar.isNullOrBlank()) {
                avatar = IdentityGenerator.generateRandomAvatarId()
                prefs.edit { putString("avatar_id", avatar) }
            }
            _avatarId.value = avatar

            _themeMode.value = prefs.getString("theme_mode", "System Default") ?: "System Default"
            _satellitePort.value = prefs.getInt("satellite_port", 9090)
            _satelliteEnabled.value = prefs.getBoolean("satellite_enabled", false)

            var uri = prefs.getString("satellite_root_uri", null)
            var rootName = prefs.getString("satellite_root_name", null)
            
            // If empty OR old default, migrate to /sdcard/Test
            if (uri.isNullOrBlank() || uri.contains("/files/shared")) {
                val testDir = File(Environment.getExternalStorageDirectory(), "Test")
                uri = testDir.absolutePath
                rootName = "Test Folder"
                prefs.edit { 
                    putString("satellite_root_uri", uri)
                    putString("satellite_root_name", rootName)
                }
            }
            _satelliteRootUri.value = uri
            _satelliteRootName.value = rootName

            _showHiddenFiles.value = prefs.getBoolean("show_hidden_files", false)
            _notificationsEnabled.value = prefs.getBoolean("notifications_enabled", true)
            _initialized = true
        }
    }

    actual fun setUsername(name: String) { _username.value = name; prefs.edit { putString("username", name) } }
    actual fun setAvatarId(id: String) { _avatarId.value = id; prefs.edit { putString("avatar_id", id) } }
    actual fun updateThemeMode(mode: String) { _themeMode.value = mode; prefs.edit { putString("theme_mode", mode) } }
    
    actual fun setSatelliteEnabled(enabled: Boolean) { 
        _satelliteEnabled.value = enabled 
        prefs.edit { putBoolean("satellite_enabled", enabled) }
    }

    actual fun setSatellitePort(port: Int) { _satellitePort.value = port; prefs.edit { putInt("satellite_port", port) } }

    actual fun setSatelliteRootUri(uri: String?, name: String?) {
        _satelliteRootUri.value = uri
        _satelliteRootName.value = name
        prefs.edit { 
            putString("satellite_root_uri", uri)
            putString("satellite_root_name", name)
        }
    }

    actual fun setShowHiddenFiles(show: Boolean) { _showHiddenFiles.value = show; prefs.edit { putBoolean("show_hidden_files", show) } }
    actual fun setNotificationsEnabled(enabled: Boolean) { _notificationsEnabled.value = enabled; prefs.edit { putBoolean("notifications_enabled", enabled) } }

    actual fun resetIdentity() {
        prefs.edit { 
            remove("node_id")
            remove("username")
            remove("avatar_id")
            remove("satellite_root_uri")
            remove("satellite_root_name")
            remove("satellite_enabled")
        }
        _initialized = false
        loadFromPrefs()
    }

    companion object {
        private var _initialized = false
        private val _nodeId = MutableStateFlow("")
        private val _username = MutableStateFlow("Pilot")
        private val _avatarId = MutableStateFlow("rocket")
        private val _themeMode = MutableStateFlow("System Default")
        private val _satelliteEnabled = MutableStateFlow(false)
        private val _satellitePort = MutableStateFlow(9090)
        private val _satelliteRootUri = MutableStateFlow<String?>(null)
        private val _satelliteRootName = MutableStateFlow<String?>(null)
        private val _showHiddenFiles = MutableStateFlow(false)
        private val _notificationsEnabled = MutableStateFlow(true)
    }
}
