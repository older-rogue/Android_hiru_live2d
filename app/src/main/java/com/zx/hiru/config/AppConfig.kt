package com.zx.hiru.config

/**
 * 应用统一配置管理
 *
 * 作为运行时配置访问入口，从 RuntimeConfigRepository 读取配置
 * 不再保存硬编码的配置值
 */
object AppConfig {

    private var repository: RuntimeConfigRepository? = null
    private var cachedConfig: RuntimeApiConfig? = null

    /**
     * 初始化配置仓库
     * 必须在使用前调用
     */
    fun init(repository: RuntimeConfigRepository) {
        this.repository = repository
        this.cachedConfig = null
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): RuntimeApiConfig {
        if (cachedConfig == null) {
            cachedConfig = repository?.load() ?: RuntimeApiConfig()
        }
        return cachedConfig!!
    }

    /**
     * 刷新配置缓存
     * 配置保存后调用
     */
    fun refreshConfig() {
        cachedConfig = repository?.load()
    }

    /**
     * AI 大模型配置
     */
    object Ai {
        val baseUrl: String get() = getConfig().ai.baseUrl
        val apiKey: String get() = getConfig().ai.apiKey
        val model: String get() = getConfig().ai.model
        val timeoutMs: Long get() = getConfig().ai.timeoutMs.toLongOrNull() ?: 30_000L
        val systemPrompt: String get() = getConfig().ai.systemPrompt
    }

    /**
     * 火山引擎 ASR 配置
     */
    object Asr {
        val appId: String get() = getConfig().asr.appId
        val token: String get() = getConfig().asr.token
        val address: String get() = getConfig().asr.address
        val uri: String get() = getConfig().asr.uri
        val resourceId: String get() = getConfig().asr.resourceId
    }

    /**
     * 火山引擎 TTS 配置
     */
    object Tts {
        val appId: String get() = getConfig().tts.appId
        val token: String get() = getConfig().tts.token
        val address: String get() = getConfig().tts.address
        val uri: String get() = getConfig().tts.uri
        val resourceId: String get() = getConfig().tts.resourceId
    }
}
