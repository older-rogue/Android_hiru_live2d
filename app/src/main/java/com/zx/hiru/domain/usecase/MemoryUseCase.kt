package com.zx.hiru.domain.usecase

import com.zx.hiru.data.repository.MemoryRepository

/**
 * 记忆 UseCase
 *
 * 管理记忆的读取、更新、持久化
 */
class MemoryUseCase(
    private val memoryRepository: MemoryRepository
) {
    /**
     * 对话后更新记忆
     */
    suspend fun updateAfterConversation(userInput: String, aiResponse: String): Result<Unit> {
        return memoryRepository.updateMemory(userInput, aiResponse)
    }

    /**
     * 获取记忆上下文（用于注入 AI 提示词）
     */
    fun getMemoryContext(): String = memoryRepository.getMemoryContext()

    /**
     * 获取记忆原文
     */
    fun getMemory(): String = memoryRepository.getMemory()

    /**
     * 清空记忆
     */
    fun clearMemory() = memoryRepository.clearMemory()
}
