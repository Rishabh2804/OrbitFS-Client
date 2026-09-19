package org.orbitfs.common.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.orbitfs.common.util.PlatformContext
import java.io.File
import java.util.*

actual class SettingsRepository actual constructor(context: PlatformContext) {
    private val settingsFile = File(System.getProperty("user.home"), ".orbitfs_settings.properties")
    private val props = Properties()

    private val _nodeId = MutableStateFlow("")
    actual val nodeId: StateFlow<String> = _nodeId

    private val _username = MutableStateFlow("")
    actual val username: StateFlow<String> = _username

    private val _avatarId = MutableStateFlow("rocket")
    actual val avatarId: StateFlow<String> = _avatarId

    private val _themeMode = MutableStateFlow("System Default")
    actual val themeMode: StateFlow<String> = _themeMode

    private val _satelliteEnabled = MutableStateFlow(false)
    actual val satelliteEnabled: StateFlow<Boolean> = _satelliteEnabled

    private val _satellitePort = MutableStateFlow(9090)
    actual val satellitePort: StateFlow<Int> = _satellitePort

    private val _satelliteRootUri = MutableStateFlow<String?>(null)
    actual val satelliteRootUri: StateFlow<String?> = _satelliteRootUri

    private val _showHiddenFiles = MutableStateFlow(false)
    actual val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles

    private val _notificationsEnabled = MutableStateFlow(true)
    actual val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    init { load() }

    private fun load() {
        if (settingsFile.exists()) settingsFile.inputStream().use { props.load(it) }

        var id = props.getProperty("node_id")
        if (id == null) {
            id = UUID.randomUUID().toString()
            props.setProperty("node_id", id)
            save()
        }
        _nodeId.value = id

        _username.value = props.getProperty("username", "Pilot-${UUID.randomUUID().toString().takeLast(4)}")
        _avatarId.value = props.getProperty("avatar_id", "rocket")
        _themeMode.value = props.getProperty("theme_mode", "System Default")
        _satelliteEnabled.value = props.getProperty("satellite_enabled", "false").toBoolean()
        _satellitePort.value = props.getProperty("satellite_port", "9090").toIntOrNull() ?: 9090
        _satelliteRootUri.value = props.getProperty("satellite_root_uri") ?: props.getProperty("satellite_root")
        _showHiddenFiles.value = props.getProperty("show_hidden", "false").toBoolean()
        _notificationsEnabled.value = props.getProperty("notifications", "true").toBoolean()
    }

    private fun save() {
        props.setProperty("node_id", _nodeId.value)
        props.setProperty("username", _username.value)
        props.setProperty("avatar_id", _avatarId.value)
        props.setProperty("theme_mode", _themeMode.value)
        props.setProperty("satellite_enabled", _satelliteEnabled.value.toString())
        props.setProperty("satellite_port", _satellitePort.value.toString())
        _satelliteRootUri.value?.let { props.setProperty("satellite_root_uri", it) }
        props.remove("satellite_root")
        props.setProperty("show_hidden", _showHiddenFiles.value.toString())
        props.setProperty("notifications", _notificationsEnabled.value.toString())
        settingsFile.outputStream().use { props.store(it, "OrbitFS Settings") }
    }

    actual fun setUsername(name: String) { _username.value = name; save() }
    actual fun setAvatarId(id: String) { _avatarId.value = id; save() }
    actual fun updateThemeMode(mode: String) { _themeMode.value = mode; save() }
    actual fun setSatelliteEnabled(enabled: Boolean) { _satelliteEnabled.value = enabled; save() }
    actual fun setSatellitePort(port: Int) { _satellitePort.value = port; save() }
    actual fun setSatelliteRootUri(uri: String?) { _satelliteRootUri.value = uri; save() }
    actual fun setShowHiddenFiles(show: Boolean) { _showHiddenFiles.value = show; save() }
    actual fun setNotificationsEnabled(enabled: Boolean) { _notificationsEnabled.value = enabled; save() }
}
