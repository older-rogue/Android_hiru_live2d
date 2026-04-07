package com.zx.hiru.ai

import android.util.Log
import com.zx.hiru.config.AppConfig
import com.zx.hiru.util.NetworkKit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 对话内容判断器
 *
 * 使用AI判断语音识别的内容是否为有效对话
 * 用于过滤噪音、无意义声音、误触发等无效内容
 */
class DialogValidator {

    companion object {
        private const val TAG = "DialogValidator"

        // 调试开关：设置为true跳过AI验证，直接返回有效
        var DEBUG_SKIP_VALIDATION = false

        // 验证器专用的系统提示词
        private val VALIDATOR_PROMPT = """
            你是一个"二次元口袋女友"的语音交互过滤器。你的任务是判断用户的输入是否**需要**触发女友的回复。

            ### 判断规则
            请分析用户的输入文本，如果出现以下情况，请输出 `{"is_valid": false}`：
            1. **背景噪音/无意义**：如"呃..."、"那个..."、咳嗽声、或者无法理解的乱码。
            2. **对他人的对话**：用户明显是在和身边的第三人说话（例如："喂，老王，把那个递给我"、"妈，我出门了"）。
            3. **非交互指令**：用户在使用手机其他功能（例如："嘿 Siri"、"暂停音乐"、"调大音量"）。
            4. **重复/机械音**：明显的误触录音导致的重复语句。

            如果出现以下情况，请输出 `{"is_valid": true}`：
            1. **直接对话**：用户直接对女友说话（例如："早安"、"你在干嘛"、"帮我个忙"）。
            2. **情感表达**：用户的自言自语，但带有明显的情感倾诉意图（例如："今天好累啊"、"烦死了"）。
            3. **提问**：用户提出的任何问题。
            4. **模糊输入**：如果不确定用户是否在对自己说话，为了体验流畅，默认输出 `RESPOND`。

            ### 输出格式
            **重要：** 不要输出任何解释或额外文字，只返回JSON，不要有其他内容。

            ### 示例
            用户输入: "喂，把盐递给我一下。"
            输出: {"is_valid": false}

            用户输入: "宝宝，我回来了。"
            输出: {"is_valid": true}

            用户输入: "今天天气真不错，不想上班啊。"
            输出: {"is_valid": true}

            用户输入: "Siri 定个闹钟。"
            输出: {"is_valid": false}

            用户输入: "呃...那个..."
            输出: {"is_valid": false}
""".trimIndent()
    }

    private var client = NetworkKit.createHttpClient(10_000L)

    /**
     * 验证结果
     */
    @Serializable
    data class ValidationResult(
        val is_valid: Boolean
    )

    /**
     * 验证用户输入是否为有效对话
     *
     * @param input 用户输入的文本
     * @return true 如果是有效对话，false 如果是无效内容
     */
    suspend fun validate(input: String): Boolean {
        if (input.isBlank()) {
            Log.d(TAG, "Blank input, invalid")
            return false
        }

        // 调试模式：跳过验证
        if (DEBUG_SKIP_VALIDATION) {
            Log.d(TAG, "DEBUG_SKIP_VALIDATION enabled, skipping validation")
            return true
        }

        // 快速过滤：单字且不是常见有效单字
        if (input.length == 1 && !input.matches(Regex("[好是嗯对哦行可以]"))) {
            Log.d(TAG, "Single char filtered: $input")
            return false
        }

        // 快速过滤：纯语气词
        if (input.matches(Regex("^[嗯啊呃哦唔哈]+[.。…~]*$"))) {
            Log.d(TAG, "Filtered pure filler sound: $input")
            return false
        }

        Log.i(TAG, "Calling AI to validate: $input")

        return suspendCoroutine { continuation ->
            val messages = buildMessages(input)

            val request = AiRequest(
                model = AppConfig.Ai.model,
                messages = messages,
                maxTokens = 50,
                stream = false
            )

            val requestBody = NetworkKit.json.encodeToString(request)
                .toRequestBody(NetworkKit.jsonMediaType)

            val httpRequest = Request.Builder()
                .url("${AppConfig.Ai.baseUrl}/chat/completions")
                .addHeader("Authorization", "Bearer ${AppConfig.Ai.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(httpRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Validation request failed: ${e.message}")
                    // 网络失败时，默认为有效，避免阻断正常对话
                    continuation.resume(true)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "API Response body: $responseBody")

                    if (responseBody.isNullOrEmpty()) {
                        Log.e(TAG, "Empty response body")
                        continuation.resume(true)
                        return
                    }

                    try {
                        val aiResponse = NetworkKit.json.decodeFromString<AiResponse>(responseBody)
                        val text = aiResponse.getText()?.trim() ?: ""

                        Log.d(TAG, "Validator AI response text: $text")

                        // 尝试提取JSON（处理AI返回markdown代码块的情况）
                        val jsonText = extractJson(text)
                        Log.d(TAG, "Extracted JSON: $jsonText")

                        // 解析JSON结果
                        val result = runCatching {
                            NetworkKit.json.decodeFromString<ValidationResult>(jsonText)
                        }.getOrNull()

                        val isValid = result?.is_valid ?: true
                        Log.i(TAG, "Validation result for '$input': $isValid")
                        continuation.resume(isValid)

                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse validation response: ${e.message}")
                        // 解析失败时，默认为有效
                        continuation.resume(true)
                    }
                }
            })
        }
    }

    /**
     * 构建消息列表（不使用历史缓存，直接判断当前输入）
     */
    private fun buildMessages(input: String): List<AiRequest.Message> {
        val messages = mutableListOf<AiRequest.Message>()

        // 系统提示词
        messages.add(AiRequest.Message("system", VALIDATOR_PROMPT))

        // 直接发送当前输入，不添加历史上下文
        messages.add(AiRequest.Message("user", input))

        return messages
    }

    /**
     * 关闭客户端
     */
    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    /**
     * 从文本中提取JSON（处理markdown代码块）
     */
    private fun extractJson(text: String): String {
        // 尝试提取markdown代码块中的JSON
        val codeBlockMatch = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```").find(text)
        if (codeBlockMatch != null) {
            return codeBlockMatch.groupValues[1].trim()
        }

        // 尝试直接匹配JSON对象
        val jsonMatch = Regex("\\{[^{}]*\"is_valid\"[^{}]*\\}").find(text)
        if (jsonMatch != null) {
            return jsonMatch.value
        }

        return text
    }
}
