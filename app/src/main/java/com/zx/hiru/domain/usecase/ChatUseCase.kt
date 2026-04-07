package com.zx.hiru.domain.usecase

import com.zx.hiru.ai.DialogValidator
import com.zx.hiru.data.repository.AiRepository
import com.zx.hiru.data.repository.MemoryRepository
import com.zx.hiru.domain.model.ChatResponse
import com.zx.hiru.domain.model.ChatState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 对话 UseCase
 *
 * 封装完整对话流程：验证 -> 思考 -> 回复
 */
class ChatUseCase(
    private val aiRepository: AiRepository,
    private val memoryRepository: MemoryRepository,
    private val dialogValidator: DialogValidator
) {
    /**
     * 执行对话流程
     * @param input 用户输入
     * @return 对话状态流
     */
    fun execute(input: String): Flow<ChatState> = flow {
        // 1. 验证输入有效性（DialogValidator.validate 是 suspend 函数）
        emit(ChatState.Validating)
        val isValid = dialogValidator.validate(input)
        if (!isValid) {
            emit(ChatState.Filtered(input))
            return@flow
        }

        // 2. 获取记忆上下文
        val memoryContext = memoryRepository.getMemoryContext()

        // 3. 调用 AI
        emit(ChatState.Thinking)
        val result = aiRepository.chat(input, memoryContext)

        // 4. 返回结果
        when {
            result.isSuccess -> {
                val response = result.getOrThrow()
                emit(ChatState.Responding(response))
                // 5. 异步更新记忆（不阻塞响应）
                memoryRepository.updateMemory(input, response.text)
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
