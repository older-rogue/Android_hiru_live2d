package com.zx.hiru.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI API响应体
 */
@Serializable
data class AiResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val created: Long? = null,
    val model: String? = null,
    val usage: Usage? = null
) {
    @Serializable
    data class Choice(
        val index: Int = 0,
        val message: Message? = null,
        @SerialName("finish_reason")
        val finishReason: String? = null
    )

    @Serializable
    data class Message(
        val role: String? = null,
        val content: String? = null
    )

    @Serializable
    data class Usage(
        @SerialName("prompt_tokens")
        val promptTokens: Int? = null,
        @SerialName("completion_tokens")
        val completionTokens: Int? = null,
        @SerialName("total_tokens")
        val totalTokens: Int? = null
    )

    @Serializable
    data class SystemBackContent(
        val text: String? = null,
        val action: String? = null,
        val tone: String? = null,
        val delay: String? = null
    )

    /**
     * 获取AI回复文本
     * @return 回复内容，如果没有有效回复则返回null
     */
    fun getText(): String? = choices.firstOrNull()?.message?.content
}