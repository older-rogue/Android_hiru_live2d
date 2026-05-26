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
     * 保存记忆文本（由 AI 在对话中返回）
     */
    suspend fun saveMemory(memoryText: String): Result<Unit> {
        return memoryRepository.saveMemory(memoryText)
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
