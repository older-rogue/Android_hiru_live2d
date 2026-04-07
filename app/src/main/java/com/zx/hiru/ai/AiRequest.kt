package com.zx.hiru.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI API请求体
 */
@Serializable
data class AiRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val stream: Boolean = false
) {
    @Serializable
    data class Message(
        val role: String,
        val content: String
    )
}