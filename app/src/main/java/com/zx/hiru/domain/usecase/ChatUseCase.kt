package com.zx.hiru.domain.usecase

import com.zx.hiru.data.repository.AiRepository
import com.zx.hiru.data.repository.MemoryRepository
import com.zx.hiru.domain.model.ChatState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 对话 UseCase
 *
 * 单次 API 调用完成：验证 -> 思考 -> 回复 -> 记忆更新
 */
class ChatUseCase(
    private val aiRepository: AiRepository,
    private val memoryRepository: MemoryRepository
) {
    /**
     * 执行对话流程
     * @param input 用户输入
     * @return 对话状态流
     */
    fun execute(input: String): Flow<ChatState> = flow {
        // 1. 获取当前记忆
        val currentMemory = memoryRepository.getMemory()

        // 2. 调用 AI（验证、回复、记忆更新由 AI 在单次请求中完成）
        emit(ChatState.Thinking)
        val result = aiRepository.chat(input, currentMemory)

        // 3. 处理结果
        when {
            result.isSuccess -> {
                val response = result.getOrThrow()
                if (!response.isValid) {
                    emit(ChatState.Filtered(input))
                    return@flow
                }
                emit(ChatState.Responding(response))
                // 保存 AI 返回的记忆
                response.memory?.let { memoryRepository.saveMemory(it) }
            }
            result.isFailure -> {
                emit(ChatState.Error(result.exceptionOrNull()?.message ?: "Unknown error"))
            }
        }
    }

    /**
     * 清空对话历史
     */
    fun clearHistory() = aiRepository.clearHistory()
}
