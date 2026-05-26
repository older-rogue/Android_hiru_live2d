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
     * 直接保存记忆文本（由 AI 在对话中返回）
     * @param memoryText 新的记忆文本，空字符串或"无变化"表示不更新
     */
    suspend fun saveMemory(memoryText: String): Result<Unit>

    /**
     * 清空记忆
     */
    fun clearMemory()
}
