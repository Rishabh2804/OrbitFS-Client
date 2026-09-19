package org.orbitfs.common.data

import java.io.File
import java.io.OutputStream

interface LocalFileRepository {
    fun getCacheDir(): File
    fun showToast(message: String)
    fun openFile(file: File, mimeType: String)
    fun shareFile(file: File, mimeType: String)
    
    // Download specific
    suspend fun getDownloadOutputStream(fileName: String): Pair<String?, OutputStream?>
    fun finishDownload(id: String)
    fun deleteDownload(id: String)
}
