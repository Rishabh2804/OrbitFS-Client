package org.orbitfs.common.util

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Date
import java.util.Locale

object MimeTypeUtil {

    private val forceTextExts = setOf(
        "sh", "bash", "zsh", "py", "rb", "pl", "php", "js", "ts", "tsx", "jsx",
        "go", "rs", "c", "cpp", "cc", "cxx", "h", "hpp", "java", "kt", "kts",
        "swift", "cs", "scala", "lua", "ex", "exs", "erl", "vhd", "vhdl",
        "asm", "s", "v", "sv", "ps1", "tcl", "clj", "cljs", "cljc",
        "coffee", "ls", "pkl", "nix", "csproj", "vb", "fs", "fsx",
        "ml", "mli", "f", "for", "f90", "f95", "lhs", "cabal"
    )

    fun getMimeType(file: File, cachedData: ByteArray? = null): String {
        val extension = if (file.name.contains('.')) {
            file.name.substringAfterLast('.').lowercase(Locale.ROOT)
        } else {
            ""
        }

        if (extension in forceTextExts) {
            return "text/plain"
        }

        // Basic extension-based mapping for KMP
        val basicMime = when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt", "md", "json", "xml" -> "text/plain"
            "mp4", "mkv" -> "video/mp4"
            "mp3", "wav" -> "audio/mpeg"
            "zip", "rar", "tar", "gz" -> "application/zip"
            else -> null
        }

        if (basicMime != null) return basicMime

        if (isTextFileFormat(file, cachedData)) {
            return "text/plain"
        }

        return "application/octet-stream"
    }

    private fun isTextFileFormat(file: File, cachedData: ByteArray?): Boolean {
        val bytesToInspect = cachedData ?: run {
            if (!file.exists() || file.isDirectory || file.length() == 0L) return false
            try {
                val buffer = ByteArray(512)
                val bytesRead = FileInputStream(file).use { it.read(buffer) }
                if (bytesRead > 0) buffer.copyOfRange(0, bytesRead) else byteArrayOf()
            } catch (e: IOException) {
                return false
            }
        }

        if (bytesToInspect.isEmpty()) return false

        for (b in bytesToInspect) {
            val value = b.toInt()
            if (value == 0) return false
            if (value < 32 && value != 9 && value != 10 && value != 13) return false
        }
        return true
    }

    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
            size < 1024 * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024.0))
            else -> "%.1f GB".format(size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "Unknown"
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        val date = if (timestamp < 10000000000L) Date(timestamp * 1000) else Date(timestamp)
        return sdf.format(date)
    }
}
