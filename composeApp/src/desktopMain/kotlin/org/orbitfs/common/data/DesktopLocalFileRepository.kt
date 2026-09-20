package org.orbitfs.common.data

import java.io.File
import java.io.OutputStream
import java.awt.Desktop
import org.orbitfs.common.util.OrbitLogger

class DesktopLocalFileRepository : LocalFileRepository {
    private val TAG = "DesktopRepo"

    override fun getCacheDir(): File {
        val dir = File(System.getProperty("user.home"), ".orbitfs/cache").apply { mkdirs() }
        return dir
    }

    override fun showToast(message: String) {
        println("TOAST: $message")
    }

    override fun openFile(file: File, mimeType: String) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            } else {
                // Fallback for some Linux/systems
                val process = Runtime.getRuntime().exec(arrayOf("open", file.absolutePath))
                if (process.waitFor() != 0) {
                     Runtime.getRuntime().exec(arrayOf("xdg-open", file.absolutePath))
                }
            }
        } catch (e: Exception) {
            OrbitLogger.e(TAG, "Failed to open file on desktop", e)
        }
    }

    override fun shareFile(file: File, mimeType: String) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browseFileDirectory(file)
            }
        } catch (e: Exception) {
            OrbitLogger.e(TAG, "Failed to browse directory", e)
        }
    }

    override suspend fun getDownloadOutputStream(fileName: String, targetDirUri: String?): Pair<String?, OutputStream?> {
        val dir = File(System.getProperty("user.home"), "Downloads/OrbitFS").apply { mkdirs() }
        val file = File(dir, fileName)
        return file.absolutePath to file.outputStream()
    }

    override fun finishDownload(id: String) {
        println("Download finished: $id")
    }

    override fun deleteDownload(id: String) {
        File(id).delete()
    }
}
