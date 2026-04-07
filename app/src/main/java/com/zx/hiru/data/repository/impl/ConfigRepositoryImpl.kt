package com.zx.hiru.data.repository.impl

import com.zx.hiru.config.RuntimeApiConfig
import com.zx.hiru.config.RuntimeConfigRepository
import com.zx.hiru.data.repository.ConfigRepository

/**
 * Config Repository 实现
 *
 * 适配现有 RuntimeConfigRepository
 */
class ConfigRepositoryImpl(
    private val runtimeConfigRepository: RuntimeConfigRepository
) : ConfigRepository {

    override fun loadConfig(): RuntimeApiConfig {
        return runtimeConfigRepository.load()
    }

    override fun saveConfig(config: RuntimeApiConfig) {
        runtimeConfigRepository.save(config)
    }
}
