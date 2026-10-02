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
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("mac") -> {
                    ProcessBuilder("open", file.absolutePath).start()
                }
                Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN) -> {
                    Desktop.getDesktop().open(file)
                }
                else -> {
                    if (os.contains("win")) {
                        ProcessBuilder("cmd", "/c", "start", "", file.absolutePath).start()
                    } else {
                        ProcessBuilder("xdg-open", file.absolutePath).start()
                    }
                }
            }
        } catch (e: Exception) {
            OrbitLogger.e(TAG, "Failed to open file on desktop", e)
        }
    }

    override fun shareFile(file: File, mimeType: String) {
        try {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("mac") -> {
                    ProcessBuilder("open", "-R", file.absolutePath).start()
                }
                Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR) -> {
                    Desktop.getDesktop().browseFileDirectory(file)
                }
                else -> {
                    val parent = file.parentFile ?: file
                    openFile(parent, "")
                }
            }
        } catch (e: Exception) {
            OrbitLogger.e(TAG, "Failed to browse/reveal directory on desktop", e)
        }
    }

    override suspend fun getDownloadOutputStream(fileName: String, targetDirUri: String?): Pair<String?, OutputStream?> {
        val dir = if (!targetDirUri.isNullOrBlank()) {
            File(targetDirUri)
        } else {
            File(System.getProperty("user.home"), "Downloads/OrbitFS")
        }
        dir.mkdirs()
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
