package org.orbitfs.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.orbitfs.android.client.ConnectionManager
import org.orbitfs.android.data.HostRepository

class FileBrowserViewModelFactory(
    private val connectionManager: ConnectionManager,
    private val hostRepository: HostRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FileBrowserViewModel(connectionManager, hostRepository) as T
    }
}
