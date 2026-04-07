package com.zx.hiru.ui.main

/**
 * 主页 UI 状态
 */
data class MainUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isProcessing: Boolean = false,
    val needsConfig: Boolean = false,
    val error: String? = null
) {
    val isIdle: Boolean
        get() = !isListening && !isSpeaking && !isProcessing
}
