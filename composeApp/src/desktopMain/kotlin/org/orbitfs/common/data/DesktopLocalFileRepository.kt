package org.orbitfs.common.data

import java.io.File
import java.io.OutputStream
import java.awt.Desktop

class DesktopLocalFileRepository : LocalFileRepository {
    override fun getCacheDir(): File {
        val dir = File(System.getProperty("user.home"), ".orbitfs/cache").apply { mkdirs() }
        return dir
    }

    override fun showToast(message: String) {
        println("TOAST: $message")
    }

    override fun openFile(file: File, mimeType: String) {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(file)
        }
    }

    override fun shareFile(file: File, mimeType: String) {
        // Desktop share logic (maybe open folder?)
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browseFileDirectory(file)
        }
    }

    override suspend fun getDownloadOutputStream(fileName: String): Pair<String?, OutputStream?> {
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
