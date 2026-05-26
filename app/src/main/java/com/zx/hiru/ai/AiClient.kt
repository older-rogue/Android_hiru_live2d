package com.zx.hiru.ai

import android.util.Log
import com.zx.hiru.util.NetworkKit
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * AI对话客户端
 *
 * 兼容OpenAI协议的大模型对话工具类
 *
 * 使用示例：
 * ```kotlin
 * val client = AiClient(
 *     AiConfig(
 *         baseUrl = "https://api.openai.com",
 *         apiKey = "sk-xxx",
 *         model = "gpt-4o"
 *     )
 * )
 * val response = client.chat("你好") // suspend函数
 * ```
 */
class AiClient(private val config: AiConfig) {

    companion object {
        private const val TAG = "AiClient"
    }

    private val list = mutableListOf<AiRequest.Message>()

    private val client = NetworkKit.createHttpClient(config.timeoutMs)

    /**
     * 发送消息并获取AI回复
     *
     * @param input 用户输入的消息
     * @param currentMemory 当前记忆原文（空字符串表示无记忆）
     * @return AI的回复（包含验证结果、台词、记忆更新）
     * @throws AiException 如果请求失败或响应解析失败
     */
    suspend fun chat(input: String, currentMemory: String = ""): AiResponse.SystemBackContent {
        if (list.isEmpty()) {
            val systemPrompt = buildSystemPrompt(currentMemory)
            list.add(AiRequest.Message("system", systemPrompt))
        } else {
            // 更新第一条系统消息（包含最新记忆）
            val systemPrompt = buildSystemPrompt(currentMemory)
            list[0] = AiRequest.Message("system", systemPrompt)
        }
        if (list.size > 20) {
            list.removeAt(1)
        }
        list.add(AiRequest.Message(role = "user", content = input))
        val response = chatWithMessages(list)
        // 无效对话不保留在历史中，避免污染上下文
        if (!response.is_valid) {
            // 移除刚添加的 user 消息和 assistant 回复
            if (list.size >= 2) {
                list.removeAt(list.size - 1) // assistant
                list.removeAt(list.size - 1) // user
            }
            Log.d(TAG, "Filtered message removed from history, remaining: ${list.size}")
        }
        return response
    }

    private fun buildSystemPrompt(currentMemory: String): String {
        return if (currentMemory.isNotEmpty()) {
            "${config.systemPrompt}\n\n【当前记忆】$currentMemory"
        } else {
            config.systemPrompt
        }
    }

    /**
     * 发送带历史上下文的消息并获取AI回复
     *
     * @param messages 消息列表，包含历史对话
     * @return AI的回复文本
     * @throws AiException 如果请求失败或响应解析失败
     */
    suspend fun chatWithMessages(messages: List<AiRequest.Message>): AiResponse.SystemBackContent {
        return suspendCoroutine { coroutine ->
            val request = AiRequest(
                model = config.model,
                messages = messages,
                temperature = config.temperature,
                maxTokens = config.maxTokens,
                stream = false,
            )

            val requestJson = NetworkKit.json.encodeToString(request)
            val requestBody = requestJson.toRequestBody(NetworkKit.jsonMediaType)

            val baseUrl = config.baseUrl.trimEnd('/')
            val url = "$baseUrl/chat/completions"
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val startTime = System.currentTimeMillis()

            Log.i(TAG, "=== AI Request ===")
            Log.i(TAG, "URL: $url")
            Log.i(
                TAG,
                "Model: ${config.model}, Messages: ${messages.size}, MaxTokens: ${config.maxTokens}"
            )
            Log.d(TAG, "Request body: ${messages.subList(1, messages.size)}")

            client.newCall(httpRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.e(TAG, "Request failed after ${elapsed}ms: ${e.message}")
                    coroutine.resumeWithException(AiException("网络出问题了"))
                }

                override fun onResponse(call: Call, response: Response) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        Log.e(TAG, "Empty response body, elapsed: ${elapsed}ms")
                        coroutine.resumeWithException(AiException("无返回"))
                        return
                    }

                    Log.i(TAG, "=== AI Response (${elapsed}ms) ===")
                    Log.d(TAG, "Response body: $responseBody")

                    val aiResponse = NetworkKit.json.decodeFromString<AiResponse>(responseBody)
                    val text = aiResponse.getText()
                    Log.i(TAG, "Parsed text: $text")
                    if (text.isNullOrEmpty()) {
                        Log.e(TAG, "Response contained no text, elapsed: ${elapsed}ms")
                        coroutine.resumeWithException(AiException("无返回"))
                        return
                    }
                    list.add(AiRequest.Message(role = "assistant", content = text))
                    runCatching {
                        val systemBackContent =
                            NetworkKit.json.decodeFromString<AiResponse.SystemBackContent>(text)
                        Log.i(
                            TAG,
                            "Parsed: is_valid=${systemBackContent.is_valid}, action=${systemBackContent.action}, tone=${systemBackContent.tone}, memory=${systemBackContent.memory}"
                        )
                        coroutine.resume(systemBackContent)
                    }.onFailure {
                        Log.e(TAG, "JSON parse failed, raw text: $text")
                        coroutine.resumeWithException(AiException("json格式有误"))
                    }

                }
            })
        }
    }

    /**
     * 关闭客户端并释放资源
     */
    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}

/**
 * AI客户端异常
 */
class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)
