package org.orbitfs.android.client

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import timber.log.Timber

/**
 * Global receiver for notification actions like cancelling a download.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val path = intent.getStringExtra(EXTRA_FILE_PATH) ?: return
        
        Timber.d("NotificationActionReceiver: action=$action path=$path")
        
        if (action == ACTION_CANCEL_DOWNLOAD) {
            // Dismiss right away as requested
            val nm = context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(path.hashCode())
            
            // Signal the app to cancel this download job
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
