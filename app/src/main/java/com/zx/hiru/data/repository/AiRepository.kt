package com.zx.hiru.data.repository

import com.zx.hiru.domain.model.ChatResponse

/**
 * AI 对话仓库接口
 */
interface AiRepository {
    /**
     * 发送消息并获取 AI 回复
     * @param input 用户输入
     * @param memoryContext 记忆上下文
     * @return 对话响应结果
     */
    suspend fun chat(input: String, memoryContext: String?): Result<ChatResponse>

    /**
     * 清空对话历史
     */
    fun clearHistory()
}
