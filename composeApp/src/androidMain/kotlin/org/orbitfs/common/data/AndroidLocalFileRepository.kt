package org.orbitfs.common.data

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import org.orbitfs.common.util.PlatformContext
import java.io.File
import java.io.OutputStream

class AndroidLocalFileRepository(private val platformContext: PlatformContext) : LocalFileRepository {
    private val context = platformContext.context

    override fun getCacheDir(): File = context.cacheDir

    override fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun openFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "org.orbitfs.android.kmp.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open with"))
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

    override suspend fun getDownloadOutputStream(fileName: String): Pair<String?, OutputStream?> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloadsDir, "OrbitFS").apply { mkdirs() }
        val file = File(dir, fileName)
        return file.absolutePath to file.outputStream()
    }

    override fun finishDownload(id: String) {}
    override fun deleteDownload(id: String) {}
}
