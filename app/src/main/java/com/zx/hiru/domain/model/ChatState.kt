package com.zx.hiru.domain.model

/**
 * 对话状态
 */
sealed class ChatState {
    /** 空闲状态 */
    object Idle : ChatState()

    /** 正在验证输入 */
    object Validating : ChatState()

    /** 内容被过滤 */
    data class Filtered(val text: String) : ChatState()

    /** AI 正在思考 */
    object Thinking : ChatState()

    /** AI 正在回复 */
    data class Responding(val response: ChatResponse) : ChatState()

    /** 发生错误 */
    data class Error(val message: String) : ChatState()
}
