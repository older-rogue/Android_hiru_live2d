package com.zx.hiru.ai

import android.content.Context
import android.util.Log
import com.zx.hiru.config.AppConfig
import com.zx.hiru.util.NetworkKit
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * 记忆管理器 - 维护用户的长期记忆档案
 *
 * 核心功能：
 * 1. 从对话中提取关键信息（喜好、习惯、重要事件等）
 * 2. 语义合并新旧记忆，避免冲突和冗余
 * 3. 限制记忆长度，控制 Token 消耗
 * 4. 持久化存储到 SharedPreferences
 *
 * 使用方式：
 * ```
 * MemoryManager.getInstance().init(context)
 * MemoryManager.getInstance().processConversation(userInput, aiResponse)
 * val memory = MemoryManager.getInstance().getMemoryContext()
 * ```
 */
class MemoryManager private constructor() {

    companion object {
        private const val TAG = "MemoryManager"

        // 存储相关
        private const val PREFS_NAME = "ai_memory"
        private const val KEY_MEMORY = "memory_text"

        // 记忆限制
        private const val MAX_MEMORY_CHARS = 500

        @Volatile
        private var instance: MemoryManager? = null

        fun getInstance(): MemoryManager =
            instance ?: synchronized(this) {
                instance ?: MemoryManager().also { instance = it }
            }
    }

    // HTTP 客户端
    private val httpClient = NetworkKit.createHttpClient(15_000L)

    // 应用上下文
    private var context: Context? = null

    // 当前记忆文本
    private var memoryText: String = ""

    // 记忆提取系统提示词
    private val systemPrompt = """
你是一个记忆管理助手。你的任务是维护关于用户的记忆档案。

### 规则
1. 从对话中提取关于用户的重要信息：喜好、习惯、工作、家庭、关系、重要事件等
2. 将新信息与现有记忆**语义合并**：
   - 如果新信息与旧记忆冲突，用新信息替换（如"喜欢苹果"→"喜欢香蕉，不喜欢苹果"）
   - 如果新信息是对旧记忆的补充，合并它们
   - 如果新信息无关紧要，保持原记忆不变
3. 记忆要简洁、准确，去除冗余

### 输出格式
直接输出更新后的记忆文本，不要有任何额外内容。
如果没有需要记忆的信息，输出"无变化"。

### 示例
现有记忆：用户喜欢吃苹果
新对话：用户说"我现在喜欢吃香蕉不喜欢吃苹果了"
输出：用户喜欢吃香蕉，不喜欢吃苹果

现有记忆：用户是程序员，住在上海
新对话：用户说"今天天气不错"
输出：无变化

现有记忆：无
新对话：用户说"我有一个妹妹"
输出：用户有一个妹妹
""".trimIndent()

    /**
     * 初始化记忆管理器
     * 必须在使用前调用，加载持久化的记忆
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        loadMemory()
    }

    /**
     * 从 SharedPreferences 加载记忆
     */
    private fun loadMemory() {
        runCatching {
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.getString(KEY_MEMORY, "")
                .orEmpty()
        }.onSuccess { loaded ->
            memoryText = loaded
            if (memoryText.isNotEmpty()) {
                Log.i(TAG, "记忆已加载: ${memoryText.take(100)}...")
            }
        }.onFailure { e ->
            Log.e(TAG, "加载记忆失败: ${e.message}")
            memoryText = ""
        }
    }

    /**
     * 保存记忆到 SharedPreferences
     */
    private fun saveMemory() {
        runCatching {
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.putString(KEY_MEMORY, memoryText)
                ?.apply()
            Log.i(TAG, "记忆已保存: ${memoryText.take(100)}...")
        }.onFailure { e ->
            Log.e(TAG, "保存记忆失败: ${e.message}")
        }
    }

    /**
     * 获取记忆上下文（用于注入到 AI 对话）
     * @return 格式化的记忆文本，如果无记忆则返回空字符串
     */
    fun getMemoryContext(): String =
        if (memoryText.isEmpty()) "" else "【关于用户的记忆】$memoryText"

    /**
     * 获取记忆原文
     */
    fun getMemory(): String = memoryText

    /**
     * 清空记忆
     */
    fun clearMemory() {
        memoryText = ""
        saveMemory()
        Log.i(TAG, "记忆已清空")
    }

    /**
     * 处理对话并更新记忆
     *
     * @param userInput 用户输入
     * @param aiResponse AI 回复
     * @param onComplete 完成回调（可选）
     */
    fun processConversation(
        userInput: String,
        aiResponse: String,
        onComplete: (() -> Unit)? = null
    ) {
        if (userInput.isBlank()) {
            onComplete?.invoke()
            return
        }

        Log.i(TAG, "开始处理对话，更新记忆...")

        val request = buildMemoryUpdateRequest(userInput, aiResponse)
        executeRequest(request, onComplete)
    }

    /**
     * 构建记忆更新请求
     */
    private fun buildMemoryUpdateRequest(userInput: String, aiResponse: String): Request {
        val messages = buildMessages(userInput, aiResponse)

        val aiRequest = AiRequest(
            model = AppConfig.Ai.model,
            messages = messages,
            maxTokens = 300,
            stream = false
        )

        val requestBody = NetworkKit.json.encodeToString(aiRequest)
            .toRequestBody(NetworkKit.jsonMediaType)

        return Request.Builder()
            .url("${AppConfig.Ai.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${AppConfig.Ai.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
    }

    /**
     * 构建消息列表
     */
    private fun buildMessages(userInput: String, aiResponse: String): List<AiRequest.Message> {
        val existingMemoryInfo = if (memoryText.isNotEmpty()) {
            "现有记忆：$memoryText"
        } else {
            "现有记忆：无"
        }

        return listOf(
            AiRequest.Message("system", systemPrompt),
            AiRequest.Message("user", existingMemoryInfo),
            AiRequest.Message("assistant", "好的，请提供新的对话内容。"),
            AiRequest.Message("user", "用户说：$userInput\nAI回复：$aiResponse\n\n请更新记忆。")
        )
    }

    /**
     * 执行 API 请求
     */
    private fun executeRequest(request: Request, onComplete: (() -> Unit)?) {
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "记忆更新请求失败: ${e.message}")
                onComplete?.invoke()
            }

            override fun onResponse(call: Call, response: Response) {
                handleResponse(response)
                onComplete?.invoke()
            }
        })
    }

    /**
     * 处理 API 响应
     */
    private fun handleResponse(response: Response) {
        val responseBody = response.body?.string()
        if (responseBody.isNullOrEmpty()) {
            Log.e(TAG, "响应体为空")
            return
        }

        val result = runCatching {
            NetworkKit.json.decodeFromString<AiResponse>(responseBody)
                .getText()
                ?.trim()
                .orEmpty()
        }

        result.onSuccess { text ->
            Log.d(TAG, "记忆更新响应: $text")

            when {
                text == "无变化" || text.isBlank() -> {
                    Log.d(TAG, "记忆无变化")
                }
                else -> {
                    updateMemory(text)
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "解析记忆响应失败: ${e.message}")
        }
    }

    /**
     * 更新记忆文本
     */
    private fun updateMemory(newMemory: String) {
        memoryText = if (newMemory.length > MAX_MEMORY_CHARS) {
            val truncated = newMemory.take(MAX_MEMORY_CHARS)
            Log.d(TAG, "记忆已截断至 $MAX_MEMORY_CHARS 字符")
            truncated
        } else {
            newMemory
        }

        saveMemory()
        Log.i(TAG, "记忆已更新: $memoryText")
    }
}
