package com.zx.hiru.data.repository.impl

import com.zx.hiru.ai.AiClient
import com.zx.hiru.ai.AiConfig
import com.zx.hiru.config.AppConfig
import com.zx.hiru.data.repository.AiRepository
import com.zx.hiru.domain.model.ChatResponse
import com.zx.hiru.domain.model.toChatResponse

/**
 * AI Repository 实现
 *
 * 注意：需要在首次使用前调用 init() 初始化 AiClient
 */
class AiRepositoryImpl : AiRepository {

    private var aiClient: AiClient? = null

    /**
     * 初始化 AI 客户端
     * @param config AI 配置，如果为 null 则从 AppConfig 加载
     */
    fun init(config: AiConfig? = null) {
        aiClient?.close()
        val aiConfig = config ?: AiConfig.fromRuntimeConfig(AppConfig.getConfig().ai)
        aiClient = AiClient(aiConfig)
    }

    override suspend fun chat(input: String, memoryContext: String?): Result<ChatResponse> {
        // 如果未初始化，自动使用默认配置初始化
        if (aiClient == null) {
            init()
        }
        val client = aiClient ?: return Result.failure(IllegalStateException("AiClient not initialized"))
        return runCatching {
            val response = client.chat(input, memoryContext)
            response.toChatResponse()
        }
    }

    override fun clearHistory() {
        // AiClient 内部管理历史，这里需要重新创建客户端
        init()
    }

    fun close() {
        aiClient?.close()
        aiClient = null
    }
}
