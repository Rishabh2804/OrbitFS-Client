package org.orbitfs.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.data.HostRepository
import org.orbitfs.android.data.LocalFileRepository
import org.orbitfs.android.data.SettingsRepository

class FileBrowserViewModelFactory(
    private val connectionManager: ConnectionManager,
    private val hostRepository: HostRepository,
    private val settingsRepository: SettingsRepository,
    private val localFileRepository: LocalFileRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FileBrowserViewModel(
            connectionManager = connectionManager,
            hostRepository = hostRepository,
            settingsRepository = settingsRepository,
            localFileRepository = localFileRepository
        ) as T
    }
}
