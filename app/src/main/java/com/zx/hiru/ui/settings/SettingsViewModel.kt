package com.zx.hiru.ui.settings

import androidx.lifecycle.ViewModel
import com.zx.hiru.config.RuntimeApiConfig
import com.zx.hiru.domain.usecase.ConfigUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置页 ViewModel
 */
class SettingsViewModel(
    private val configUseCase: ConfigUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    /**
     * 加载配置
     */
    fun loadConfig() {
        val config = configUseCase.loadConfig()
        _uiState.value = _uiState.value.copy(config = config)
    }

    /**
     * 保存配置
     */
    fun saveConfig(config: RuntimeApiConfig) {
        configUseCase.saveConfig(config)
        _uiState.value = _uiState.value.copy(config = config, saved = true)
    }

    /**
     * 重置保存状态
     */
    fun resetSaved() {
        _uiState.value = _uiState.value.copy(saved = false)
    }
}
