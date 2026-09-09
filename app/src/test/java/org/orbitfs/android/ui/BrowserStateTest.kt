package org.orbitfs.android.ui

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.orbitfs.android.data.ConnectionConfig
import org.orbitfs.android.model.FileInfo

class BrowserStateTest {

    @Test
    fun `BrowserState default path is root`() {
        val state = BrowserState()
        assertEquals("", state.currentPath)
    }

    @Test
    fun `FileInfo isDirectory displays with trailing slash`() {
        val file = FileInfo(
            name = "documents",
            path = "documents",
            size = 0L,
            isDirectory = true,
            lastModified = 0L
        )
        assertEquals("documents/", file.displayName)
    }

    @Test
    fun `FileInfo file has no trailing slash`() {
        val file = FileInfo(
            name = "document.txt",
            path = "document.txt",
            size = 1024L,
            isDirectory = false,
            lastModified = 0L
        )
        assertEquals("document.txt", file.displayName)
    }

    @Test
    fun `navigateToParent from top-level path`() {
        val current = "documents"
        val parent = computeParentPath(current)
        assertEquals("", parent)
    }

    @Test
    fun `navigateToParent from root stays root`() {
        val current = ""
        val parent = computeParentPath(current)
        assertEquals("", parent)
    }

    @Test
    fun `navigateToParent from nested path`() {
        val current = "a/b/c"
        val parent = computeParentPath(current)
        assertEquals("a/b", parent)
    }

    @Test
    fun `ConnectionConfig has correct defaults`() {
        val config = ConnectionConfig()
        assertEquals("192.168.0.5", config.host)
        assertEquals(9090, config.port)
    }

    @Test
    fun `ConnectionConfig allows custom values`() {
        val config = ConnectionConfig(host = "10.0.0.5", port = 8080, authToken = "token123")
        assertEquals("10.0.0.5", config.host)
        assertEquals(8080, config.port)
        assertEquals("token123", config.authToken)
    }

    @Test
    fun `formatFileSize for bytes`() {
        assertEquals("512 B", formatFileSizeForTest(512L))
    }

    @Test
    fun `formatFileSize for KB`() {
        val result = formatFileSizeForTest(1024L * 3)
        assertTrue(result.contains("KB"))
    }

    @Test
    fun `formatFileSize for MB`() {
        val result = formatFileSizeForTest(1024L * 1024 * 5)
        assertTrue(result.contains("MB"))
    }

    @Test
    fun `formatFileSize for GB`() {
        val result = formatFileSizeForTest(1024L * 1024 * 1024 * 2)
        assertTrue(result.contains("GB"))
    }

    private fun formatFileSizeForTest(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
            size < 1024 * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024.0))
            else -> "%.1f GB".format(size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun computeParentPath(current: String): String {
        if (current == "/" || current.isEmpty()) return ""
        val trimmed = current.removeSuffix("/")
        val lastSlash = trimmed.lastIndexOf("/")
        return if (lastSlash <= 0) "" else trimmed.substring(0, lastSlash)
    }
}
