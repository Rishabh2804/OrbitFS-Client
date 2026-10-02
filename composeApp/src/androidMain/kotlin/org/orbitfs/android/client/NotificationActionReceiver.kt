package org.orbitfs.android.client

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Global receiver for notification actions like cancelling a download.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val path = intent.getStringExtra(EXTRA_FILE_PATH) ?: return
        
        if (action == ACTION_CANCEL_DOWNLOAD) {
            val nm = context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(path.hashCode())
            
            NotificationSignals.cancelRequest.tryEmit(path)
        }
    }

    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "org.orbitfs.android.ACTION_CANCEL_DOWNLOAD"
        const val EXTRA_FILE_PATH = "extra_file_path"
    }
}

object NotificationSignals {
    val cancelRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
}
