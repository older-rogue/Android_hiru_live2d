package com.zx.hiru.domain.usecase

import com.zx.hiru.config.RuntimeApiConfig
import com.zx.hiru.data.repository.ConfigRepository

/**
 * 配置 UseCase
 *
 * 管理运行时配置
 */
class ConfigUseCase(
    private val configRepository: ConfigRepository
) {
    /**
     * 加载配置
     */
    fun loadConfig(): RuntimeApiConfig = configRepository.loadConfig()

    /**
     * 保存配置
     */
    fun saveConfig(config: RuntimeApiConfig) = configRepository.saveConfig(config)

    /**
     * 检查配置是否完整
     */
    fun isConfigComplete(): Boolean = configRepository.loadConfig().isComplete()
}
