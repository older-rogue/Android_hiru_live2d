package com.zx.hiru.data.repository

import com.zx.hiru.config.RuntimeApiConfig

/**
 * 配置仓库接口
 */
interface ConfigRepository {
    /**
     * 加载配置
     */
    fun loadConfig(): RuntimeApiConfig

    /**
     * 保存配置
     */
    fun saveConfig(config: RuntimeApiConfig)
}
