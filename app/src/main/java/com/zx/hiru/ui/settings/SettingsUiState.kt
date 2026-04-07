package com.zx.hiru.ui.settings

import com.zx.hiru.config.RuntimeApiConfig

/**
 * 设置页 UI 状态
 */
data class SettingsUiState(
    val config: RuntimeApiConfig? = null,
    val saved: Boolean = false,
    val error: String? = null
)
