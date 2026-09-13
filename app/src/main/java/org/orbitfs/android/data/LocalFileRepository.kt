package org.orbitfs.android.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.orbitfs.android.R
import org.orbitfs.android.client.NotificationActionReceiver
import org.orbitfs.android.util.MimeTypeUtil
import java.io.File
import java.io.OutputStream

class LocalFileRepository(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val cacheDir: File get() = context.cacheDir
    val packageName: String get() = context.packageName

    fun getDownloadOutputStream(fileName: String): Pair<Uri?, OutputStream?> {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MimeTypeUtil.getMimeType(context, File(fileName)))
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            @Suppress("DEPRECATION")
            context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues.apply { put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Download") }
            )
        }

        return if (uri != null) {
            uri to context.contentResolver.openOutputStream(uri, "wt")
        } else {
            null to null
        }
    }

    fun finishDownload(uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, values, null, null)
    }

    fun deleteDownload(uri: Uri) {
        context.contentResolver.delete(uri, null, null)
    }
    
    fun openOutputStream(uri: Uri): OutputStream? {
        return context.contentResolver.openOutputStream(uri, "wt")
    }

    // --- Notification Helpers ---

    fun createNotificationChannel(channelId: String, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getCancelPendingIntent(filePath: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CANCEL_DOWNLOAD
            putExtra(NotificationActionReceiver.EXTRA_FILE_PATH, filePath)
        }
        return PendingIntent.getBroadcast(
            context, 
            filePath.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showProgressNotification(
        id: Int,
        channelId: String,
        fileName: String,
        bytesRead: Long,
        totalSize: Long,
        cancelIntent: PendingIntent? = null
    ) {
        val progress = if (totalSize > 0) (bytesRead * 100 / totalSize).toInt() else 0
        val sizeStr = "${MimeTypeUtil.formatFileSize(bytesRead)} / ${MimeTypeUtil.formatFileSize(totalSize)}"
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Downloading")
            .setContentText(fileName)
            .setSubText(sizeStr)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, totalSize <= 0)

        if (cancelIntent != null) {
            builder.addAction(R.drawable.ic_cancel, "Cancel", cancelIntent)
        }
        
        notificationManager.notify(id, builder.build())
    }

    fun showCompletedNotification(id: Int, channelId: String, fileName: String, uri: Uri?, mimeType: String) {
        val intent = if (uri != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://downloads/public_downloads"), "vnd.android.cursor.dir/download")
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            id, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Download Completed")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_download)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
        
        notificationManager.notify(id, builder.build())
    }

    fun showErrorNotification(id: Int, channelId: String, fileName: String, error: String) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Download Failed")
            .setContentText("$fileName: $error")
            .setSmallIcon(R.drawable.ic_cancel)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        notificationManager.notify(id, builder.build())
    }

    fun showCancelledNotification(id: Int, channelId: String, fileName: String) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Download Cancelled")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_cancel)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        
        notificationManager.notify(id, builder.build())
    }

    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }
}
