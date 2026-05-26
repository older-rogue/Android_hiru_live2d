package com.zx.hiru.ai

import android.content.Context
import android.util.Log

/**
 * 记忆管理器 - 维护用户的长期记忆档案
 *
 * 核心功能：
 * 1. 持久化存储用户记忆到 SharedPreferences
 * 2. 记忆长度限制，控制 Token 消耗
 * 3. 提供记忆读取接口（用于注入 AI 提示词）
 *
 * 注意：记忆的提取和合并由主 AI 对话完成（在 system prompt 中定义），
 * 此类只负责存储和读取。
 *
 * 使用方式：
 * ```
 * MemoryManager.getInstance().init(context)
 * MemoryManager.getInstance().updateMemoryText(aiGeneratedMemory)
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

    // 应用上下文
    private var context: Context? = null

    // 当前记忆文本
    private var memoryText: String = ""

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
     * 直接更新记忆文本（由 AI 在对话中返回）
     * @param text AI 返回的更新后记忆文本
     */
    fun updateMemoryText(text: String) {
        memoryText = if (text.length > MAX_MEMORY_CHARS) {
            val truncated = text.take(MAX_MEMORY_CHARS)
            Log.d(TAG, "记忆已截断至 $MAX_MEMORY_CHARS 字符")
            truncated
        } else {
            text
        }

        saveMemory()
        Log.i(TAG, "记忆已更新: $memoryText")
    }

    /**
     * 获取记忆上下文（用于注入到 AI 提示词）
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
}
