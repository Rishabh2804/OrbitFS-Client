package org.orbitfs.common.model

import java.io.File

sealed interface UiEffect {
    data class ShowToast(val message: String) : UiEffect
    data class OpenFile(val file: File, val mimeType: String) : UiEffect
    data class ShareFile(val file: File, val mimeType: String) : UiEffect
}
