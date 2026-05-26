package com.zx.hiru.domain.model

/**
 * 对话响应领域模型
 *
 * 注意：这是领域层的封装，底层使用 AiResponse.SystemBackContent
 */
data class ChatResponse(
    val text: String,
    val tone: String?,
    val action: String?,
    val isValid: Boolean = true,
    val memory: String? = null
)

/**
 * 将 AiResponse.SystemBackContent 转换为 ChatResponse
 */
fun com.zx.hiru.ai.AiResponse.SystemBackContent.toChatResponse(): ChatResponse {
    return ChatResponse(
        text = text ?: "",
        tone = tone,
        action = action,
        isValid = is_valid,
        memory = memory
    )
}
