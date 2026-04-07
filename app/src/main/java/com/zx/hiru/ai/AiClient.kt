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

    private val list = mutableListOf<AiRequest.Message>()

    private val client = NetworkKit.createHttpClient(config.timeoutMs)

    /**
     * 发送消息并获取AI回复
     *
     * @param input 用户输入的消息
     * @param memoryContext 记忆上下文（可选）
     * @return AI的回复文本
     * @throws AiException 如果请求失败或响应解析失败
     */
    suspend fun chat(input: String, memoryContext: String? = null): AiResponse.SystemBackContent {
        if (list.isEmpty()) {
            // 构建系统提示词，包含记忆上下文
            val systemPrompt = if (!memoryContext.isNullOrEmpty()) {
                "${config.systemPrompt}\n\n$memoryContext"
            } else {
                config.systemPrompt
            }
            list.add(AiRequest.Message("system", systemPrompt))
        } else if (!memoryContext.isNullOrEmpty()) {
            // 如果已有消息列表但需要更新记忆，在第一条系统消息后插入记忆
            // 查找是否有记忆消息，有则更新，无则插入
            val memoryIndex = list.indexOfFirst {
                it.role == "system" && it.content.contains("【关于用户的记忆】")
            }
            if (memoryIndex >= 0) {
                list[memoryIndex] = AiRequest.Message("system", "${config.systemPrompt}\n\n$memoryContext")
            }
        }
        if (list.size > 20) {
            list.removeAt(1)
        }
        list.add(AiRequest.Message(role = "user", content = input))
        return chatWithMessages(list)
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

            val requestBody = NetworkKit.json.encodeToString(request)
                .toRequestBody(NetworkKit.jsonMediaType)

            val baseUrl = config.baseUrl.trimEnd('/')
            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(httpRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    coroutine.resumeWithException(AiException("网络出问题了"))
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        coroutine.resumeWithException(AiException("无返回"))
                        return
                    }

                    val aiResponse = NetworkKit.json.decodeFromString<AiResponse>(responseBody)
                    val text = aiResponse.getText()
                    Log.d("AiClient",text.orEmpty())
                    if (text.isNullOrEmpty()) {
                        coroutine.resumeWithException(AiException("无返回"))
                        return
                    }
                    list.add(AiRequest.Message(role = "assistant", content = text))
                    runCatching {
                        val systemBackContent =
                            NetworkKit.json.decodeFromString<AiResponse.SystemBackContent>(text)
                        coroutine.resume(systemBackContent)
                    }.onFailure {
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
