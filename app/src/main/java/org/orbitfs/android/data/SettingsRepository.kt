package org.orbitfs.android.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("orbitfs_settings", Context.MODE_PRIVATE)

    private val _autoLoadThresholdKb = MutableStateFlow(prefs.getInt(KEY_AUTO_LOAD_THRESHOLD, DEFAULT_THRESHOLD_KB))
    val autoLoadThresholdKb: StateFlow<Int> = _autoLoadThresholdKb

    private val _showHiddenFiles = MutableStateFlow(prefs.getBoolean(KEY_SHOW_HIDDEN, false))
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles

    fun updateAutoLoadThreshold(thresholdKb: Int) {
        prefs.edit().putInt(KEY_AUTO_LOAD_THRESHOLD, thresholdKb).apply()
        _autoLoadThresholdKb.value = thresholdKb
    }

    fun toggleShowHiddenFiles() {
        val newValue = !_showHiddenFiles.value
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, newValue).apply()
        _showHiddenFiles.value = newValue
    }

    fun setShowHiddenFiles(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, show).apply()
        _showHiddenFiles.value = show
    }

    companion object {
        const val DEFAULT_THRESHOLD_KB = 100
        private const val KEY_AUTO_LOAD_THRESHOLD = "auto_load_threshold_kb"
        private const val KEY_SHOW_HIDDEN = "show_hidden_files"
    }
}