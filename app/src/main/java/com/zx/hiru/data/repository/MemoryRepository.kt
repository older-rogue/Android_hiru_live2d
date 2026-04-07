package com.zx.hiru.data.repository

/**
 * 记忆仓库接口
 */
interface MemoryRepository {
    /**
     * 获取记忆原文
     */
    fun getMemory(): String

    /**
     * 获取记忆上下文（用于注入 AI 提示词）
     */
    fun getMemoryContext(): String

    /**
     * 更新记忆
     * @param userInput 用户输入
     * @param aiResponse AI 回复
     */
    suspend fun updateMemory(userInput: String, aiResponse: String): Result<Unit>

    /**
     * 清空记忆
     */
    fun clearMemory()
}
