package org.orbitfs.common.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import org.orbitfs.android.R
import org.orbitfs.common.util.PlatformContext
import java.io.File
import java.io.OutputStream

class AndroidLocalFileRepository(private val platformContext: PlatformContext) : LocalFileRepository {
    private val context = platformContext.context
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createDownloadChannel()
    }

    private fun createDownloadChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "downloads", "File Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun getCacheDir(): File = context.cacheDir

    override fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun openFile(file: File, mimeType: String) {
        val uri = try {
            FileProvider.getUriForFile(context, "org.orbitfs.android.kmp.fileprovider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            val chooser = Intent.createChooser(intent, "Open with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            showToast("No app found to open this file")
        }
    }

    override fun shareFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "org.orbitfs.android.kmp.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share File"))
    }

    override suspend fun getDownloadOutputStream(fileName: String, targetDirUri: String?): Pair<String?, OutputStream?> {
        showDownloadNotification(fileName, 0, 100)
        
        if (targetDirUri != null) {
            val treeUri = Uri.parse(targetDirUri)
            val doc = DocumentFile.fromTreeUri(context, treeUri)
            val file = doc?.createFile("application/octet-stream", fileName)
            return file?.uri?.toString() to file?.let { context.contentResolver.openOutputStream(it.uri) }
        }
        
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloadsDir, "OrbitFS").apply { mkdirs() }
        val file = File(dir, fileName)
        return file.absolutePath to file.outputStream()
    }

    override fun finishDownload(id: String) {
        val name = id.substringAfterLast("/")
        val builder = NotificationCompat.Builder(context, "downloads")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download Complete")
            .setContentText(name)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
        
        notificationManager.notify(id.hashCode(), builder.build())
    }

    override fun deleteDownload(id: String) {}

    private fun showDownloadNotification(name: String, progress: Int, total: Int) {
        val builder = NotificationCompat.Builder(context, "downloads")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Downloading File")
            .setContentText(name)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(total, progress, total <= 0)
            .setOngoing(progress < total)
        
        notificationManager.notify(name.hashCode(), builder.build())
    }
}
