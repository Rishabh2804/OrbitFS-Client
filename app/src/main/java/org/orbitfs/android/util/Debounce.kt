package org.orbitfs.android.util

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

@Composable
fun debouncedClick(
    debounceMs: Long = 500L,
    onClick: () -> Unit
): () -> Unit {
    var lastClickTime by remember { mutableStateOf(0L) }
    return {
        val now = System.currentTimeMillis()
        if (now - lastClickTime > debounceMs) {
            lastClickTime = now
            onClick()
        }
    }
}
