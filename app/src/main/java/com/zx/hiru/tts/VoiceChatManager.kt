package com.zx.hiru.tts

import android.content.Context
import android.util.Log
import com.zx.hiru.ai.AiClient
import com.zx.hiru.ai.AiConfig
import com.zx.hiru.ai.AiResponse
import com.zx.hiru.ai.MemoryManager
import com.zx.live2d.LAppLive2DManager
import com.zx.live2d.LipSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 语音对话管理器
 *
 * 将 ASR（语音识别）→ AI（大模型）→ TTS（语音合成）串联起来
 * 实现完整的语音对话流程
 *
 * 支持连续语音识别模式：
 * - 应用启动后自动开启连续语音识别
 * - AI 在单次请求中同时完成内容验证、角色回复和记忆更新
 * - 无效内容（噪音、无意义声音、误触发）会被 AI 的 is_valid 字段过滤
 * - AI回复完成后自动重新开始监听
 *
 * 使用示例：
 * ```kotlin
 * val manager = VoiceChatManager()
 * manager.init(context)
 * manager.startContinuousListening() // 开始连续语音识别
 * ```
 */
class VoiceChatManager {

    companion object {
        private const val TAG = "VoiceChatManager"
        private const val MAX_HISTORY_COUNT = 5
    }

    // 各模块实例
    private val asrManager = AsrManager.getInstance()
    private val ttsManager = TtsManager.getInstance()

    // AI客户端
    private var aiClient: AiClient? = null

    // 记忆管理器
    private val memoryManager = MemoryManager.getInstance()

    // 协程作用域
    private val scopeJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + scopeJob)

    // 状态
    private var isListening = false
    private var isSpeaking = false
    private var isProcessing = false
    private var isContinuousMode = false  // 是否为连续监听模式

    // 对话历史（最近5轮）
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    // 回调
    private var callback: VoiceChatCallback? = null

    // === 耗时统计 ===
    private var timeAsrEnd: Long = 0      // 录音结束时间
    private var timeAiStart: Long = 0     // AI请求开始时间
    private var timeAiEnd: Long = 0       // AI响应返回时间
    private var timeTtsStart: Long = 0    // TTS合成开始时间
    private var timePlayStart: Long = 0   // 播放开始时间

    /**
     * 对话回调接口
     */
    interface VoiceChatCallback {
        /**
         * 用户说话内容
         */
        fun onUserSpeak(text: String, isFinal: Boolean)

        /**
         * AI开始思考
         */
        fun onAiThinking()

        /**
         * AI回复内容
         */
        fun onAiResponse(content: AiResponse.SystemBackContent)

        /**
         * AI开始说话
         */
        fun onAiSpeakStart(text: String)

        /**
         * AI说话结束
         */
        fun onAiSpeakComplete()

        /**
         * 重新开始监听（连续模式下）
         */
        fun onListeningRestart()

        /**
         * 内容被过滤（无效对话）
         */
        fun onContentFiltered(text: String)

        /**
         * 错误回调
         */
        fun onError(stage: String, errorCode: Int, errorMessage: String)
    }

    /**
     * 初始化
     */
    fun init(context: Context, aiConfig: AiConfig = AiConfig()) {
        asrManager.setContext(context)
        ttsManager.setContext(context)
        aiClient = AiClient(aiConfig)
        // 初始化记忆管理器
        memoryManager.init(context)
    }

    /**
     * 重新初始化（配置变更时调用）
     * @param context 应用上下文
     * @param aiConfig 新的AI配置
     */
    fun reinit(context: Context, aiConfig: AiConfig) {
        Log.i(TAG, "Reinitializing with new config")

        // 停止当前操作
        stopContinuousListening()
        stopSpeaking()

        // 重置语音引擎
        asrManager.reset()
        ttsManager.reset()

        // 关闭旧的AI客户端
        aiClient?.close()

        // 创建新的AI客户端
        aiClient = AiClient(aiConfig)

        // 重置状态
        isListening = false
        isSpeaking = false
        isProcessing = false

        Log.i(TAG, "Reinitialization complete")
    }

    /**
     * 设置回调
     */
    fun setCallback(callback: VoiceChatCallback) {
        this.callback = callback
    }

    /**
     * 设置AI客户端（使用自定义配置）
     */
    fun setAiClient(client: AiClient) {
        this.aiClient = client
    }

    /**
     * 开始语音识别
     */
    fun startListening() {
        if (isListening || isSpeaking || isProcessing) {
            Log.w(TAG, "Cannot start listening: isListening=$isListening, isSpeaking=$isSpeaking, isProcessing=$isProcessing")
            return
        }

        isListening = true
        timeAsrEnd = System.currentTimeMillis()
        Log.i(TAG, "=== [录音] 开始 ===")

        asrManager.startRecognition(object : AsrManager.AsrCallback {
            override fun onResult(text: String, isFinal: Boolean) {
                Log.d(TAG, "ASR result: $text, isFinal: $isFinal")
                callback?.onUserSpeak(text, isFinal)

                if (isFinal && text.isNotEmpty()) {
                    // 最终识别结果，停止识别并处理
                    isListening = false
                    timeAsrEnd = System.currentTimeMillis()
                    Log.i(TAG, "=== [录音] 结束 (用户: $text) ===")
                    processUserInput(text)
                }
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                Log.e(TAG, "ASR error: $errorCode - $errorMessage")
                isListening = false
                callback?.onError("ASR", errorCode, errorMessage)
                // 连续模式下，出错后尝试重新监听
                if (isContinuousMode) {
                    restartListening()
                }
            }
        })
    }

    /**
     * 开始连续语音识别模式
     * 应用启动后调用，会自动持续监听
     */
    fun startContinuousListening() {
        Log.i(TAG, "Start continuous listening mode")
        isContinuousMode = true
        startListening()
    }

    /**
     * 停止连续语音识别模式
     */
    fun stopContinuousListening() {
        Log.i(TAG, "Stop continuous listening mode")
        isContinuousMode = false
        stopListening()
    }

    /**
     * 重新开始监听（连续模式下）
     */
    fun restartListening() {
        if (!isContinuousMode) {
            Log.d(TAG, "Not in continuous mode, skip restart")
            return
        }

        if (isListening || isSpeaking || isProcessing) {
            Log.d(TAG, "Cannot restart now, will retry later")
            return
        }

        Log.i(TAG, "Restart listening")
        callback?.onListeningRestart()

        // 延迟一小段时间后重新开始监听，避免立即重启
        scope.launch {
            kotlinx.coroutines.delay(300)
            startListening()
        }
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        if (!isListening) return

        Log.i(TAG, "Stop listening")
        asrManager.stopRecognition()
        isListening = false
    }

    /**
     * 处理用户输入（发送给AI）
     * AI 在单次请求中完成验证、回复、记忆更新
     */
    private fun processUserInput(text: String) {
        if (text.isEmpty()) {
            Log.w(TAG, "Empty input, skip processing")
            return
        }

        isProcessing = true
        timeAiStart = System.currentTimeMillis()
        val asrDuration = timeAiStart - timeAsrEnd
        Log.i(TAG, "=== [AI] 请求开始 (ASR→AI 间隔: ${asrDuration}ms) ===")
        callback?.onAiThinking()

        scope.launch {
            try {
                val client = aiClient ?: throw IllegalStateException("AiClient not initialized")
                Log.i(TAG, "用户输入: $text")

                val currentMemory = memoryManager.getMemory()
                val response = withContext(Dispatchers.IO) {
                    client.chat(text, currentMemory)
                }

                timeAiEnd = System.currentTimeMillis()
                val aiDuration = timeAiEnd - timeAiStart
                Log.i(TAG, "=== [AI] 请求结束 (耗时: ${aiDuration}ms) is_valid=${response.is_valid}, text=${response.text} ===")

                // 检查验证结果
                if (!response.is_valid) {
                    Log.i(TAG, "内容被过滤: $text")
                    isProcessing = false
                    callback?.onContentFiltered(text)
                    if (isContinuousMode) {
                        restartListening()
                    }
                    return@launch
                }

                // 保存 AI 返回的记忆
                response.memory?.let { memory ->
                    if (memory.isNotBlank() && memory != "无变化") {
                        memoryManager.updateMemoryText(memory)
                    }
                }

                callback?.onAiResponse(response)

                // 添加到对话历史
                response.text?.let { aiText ->
                    addToHistory(text, aiText)
                }

                // AI回复后，进行语音合成（带情绪）
                response.text?.let { speakText ->
                    if (speakText.isNotEmpty()) {
                        val ttsEmotion = EmotionMapper.mapToTtsEmotion(response.tone)
                        Log.d(TAG, "Map tone '${response.tone}' to TTS emotion '$ttsEmotion'")
                        timeTtsStart = System.currentTimeMillis()
                        val ttsDelay = timeTtsStart - timeAiEnd
                        Log.i(TAG, "=== [TTS] 合成开始 (AI→TTS 间隔: ${ttsDelay}ms) ===")
                        speak(speakText, ttsEmotion)
                    } else {
                        // 没有回复内容，直接重新开始监听
                        isProcessing = false
                        if (isContinuousMode) {
                            restartListening()
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "AI processing error: ${e.message}")
                isProcessing = false
                callback?.onError("AI", -1, e.message ?: "Unknown error")
                if (isContinuousMode) {
                    restartListening()
                }
            }
        }
    }

    /**
     * 重新开始监听（连续模式下）
     */
    private fun restartListeningAfterSpeak() {
        isProcessing = false
        if (isContinuousMode) {
            restartListening()
        }
    }

    /**
     * 添加到对话历史
     */
    private fun addToHistory(userText: String, aiResponse: String) {
        conversationHistory.add(Pair(userText, aiResponse))
        // 保持最近5轮对话
        while (conversationHistory.size > MAX_HISTORY_COUNT) {
            conversationHistory.removeAt(0)
        }
    }

    /**
     * 语音合成（让AI说话）
     */
    fun speak(text: String, emotion: String? = null) {
        if (isSpeaking) {
            Log.w(TAG, "Already speaking, stop current first")
            ttsManager.stop()
        }

        isSpeaking = true
        Log.i(TAG, "=== [TTS] 开始 (text: $text, emotion: $emotion) ===")

        ttsManager.speak(text, emotion, object : TtsManager.TtsCallback {
            override fun onSynthesisStart(text: String) {
                val synDelay = System.currentTimeMillis() - timeTtsStart
                Log.i(TAG, "[TTS] 合成中... (TTS→合成间隔: ${synDelay}ms)")
            }

            override fun onSynthesisComplete() {
                val synDuration = System.currentTimeMillis() - timeTtsStart
                Log.i(TAG, "[TTS] 合成完成 (合成耗时: ${synDuration}ms)")
            }

            override fun onPlayStart() {
                timePlayStart = System.currentTimeMillis()
                val playDelay = timePlayStart - timeTtsStart
                Log.i(TAG, "=== [播放] 开始 (TTS→播放间隔: ${playDelay}ms) ===")
                // 启用唇同步
                LAppLive2DManager.getInstance().setLipSyncEnabled(true)
                callback?.onAiSpeakStart(text)
            }

            override fun onPlayComplete() {
                val playEnd = System.currentTimeMillis()
                val playDuration = playEnd - timePlayStart
                val totalDuration = playEnd - timeAsrEnd
                Log.i(TAG, "=== [播放] 结束 (播放耗时: ${playDuration}ms) ===")
                Log.i(TAG, "=== [流水线总览] 录音结束→播放结束 总耗时: ${totalDuration}ms ===")
                // 禁用唇同步
                LAppLive2DManager.getInstance().setLipSyncEnabled(false)
                isSpeaking = false
                callback?.onAiSpeakComplete()
                // 重新开始监听
                restartListeningAfterSpeak()
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                Log.e(TAG, "TTS error: $errorCode - $errorMessage")
                // 禁用唇同步
                LAppLive2DManager.getInstance().setLipSyncEnabled(false)
                isSpeaking = false
                callback?.onError("TTS", errorCode, errorMessage)
                // 连续模式下，出错后重新开始监听
                if (isContinuousMode) {
                    restartListening()
                }
            }
        })
    }

    /**
     * 停止说话
     */
    fun stopSpeaking() {
        if (!isSpeaking) return

        Log.i(TAG, "Stop speaking")
        ttsManager.stop()
        // 禁用唇同步
        LAppLive2DManager.getInstance().setLipSyncEnabled(false)
        isSpeaking = false
    }

    /**
     * 开始一轮新的对话（便捷方法）
     */
    fun startNewConversation() {
        if (isSpeaking) {
            stopSpeaking()
        }
        if (isListening) {
            stopListening()
        }
        startListening()
    }

    /**
     * 是否正在听
     */
    fun isListening(): Boolean = isListening

    /**
     * 是否正在说话
     */
    fun isSpeaking(): Boolean = isSpeaking

    /**
     * 是否正在处理
     */
    fun isProcessing(): Boolean = isProcessing

    /**
     * 释放资源
     */
    fun release() {
        isContinuousMode = false
        stopListening()
        stopSpeaking()
        asrManager.release()
        ttsManager.release()
        aiClient?.close()
        aiClient = null
        conversationHistory.clear()
        // 取消所有协程
        scopeJob.cancel()
    }
}
