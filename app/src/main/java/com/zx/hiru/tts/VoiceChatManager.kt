package com.zx.hiru.tts

import android.content.Context
import android.util.Log
import com.zx.hiru.ai.AiClient
import com.zx.hiru.ai.AiConfig
import com.zx.hiru.ai.AiResponse
import com.zx.hiru.ai.DialogValidator
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
 * - 在调用角色扮演AI之前，先用DialogValidator判断语音内容是否为有效对话
 * - 无效内容（噪音、无意义声音、误触发）会被过滤
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

    // 对话内容判断器
    private val dialogValidator = DialogValidator()

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

    // 当前对话（用于记忆更新）
    private var currentUserInput: String = ""
    private var currentAiResponse: String = ""

    // 回调
    private var callback: VoiceChatCallback? = null

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
        Log.i(TAG, "Start listening")

        asrManager.startRecognition(object : AsrManager.AsrCallback {
            override fun onResult(text: String, isFinal: Boolean) {
                Log.d(TAG, "ASR result: $text, isFinal: $isFinal")
                callback?.onUserSpeak(text, isFinal)

                if (isFinal && text.isNotEmpty()) {
                    // 最终识别结果，停止识别并处理
                    isListening = false
                    // 在连续模式下，先验证内容有效性
                    if (isContinuousMode) {
                        validateAndProcess(text)
                    } else {
                        processUserInput(text)
                    }
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
     * 验证内容并处理
     */
    private fun validateAndProcess(text: String) {
        scope.launch {
            try {
                Log.i(TAG, "Validating input: $text")
                val isValid = withContext(Dispatchers.IO) {
                    dialogValidator.validate(text)
                }

                if (isValid) {
                    Log.i(TAG, "Content is valid, processing...")
                    processUserInput(text)
                } else {
                    Log.i(TAG, "Content is invalid, filtered: $text")
                    callback?.onContentFiltered(text)
                    // 无效内容，重新开始监听
                    restartListening()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Validation error: ${e.message}")
                // 验证出错时，默认为有效并继续处理
                processUserInput(text)
            }
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
     */
    private fun processUserInput(text: String) {
        if (text.isEmpty()) {
            Log.w(TAG, "Empty input, skip processing")
            return
        }

        // 保存当前用户输入，用于后续记忆更新
        currentUserInput = text

        isProcessing = true
        callback?.onAiThinking()

        scope.launch {
            try {
                val client = aiClient ?: throw IllegalStateException("AiClient not initialized")
                Log.i(TAG, "Sending to AI: $text")

                // 获取记忆上下文
                val memoryContext = memoryManager.getMemoryContext()
                if (memoryContext.isNotEmpty()) {
                    Log.d(TAG, "Using memory context: ${memoryContext.take(100)}...")
                }

                val response = withContext(Dispatchers.IO) {
                    client.chat(text, memoryContext)
                }

                Log.i(TAG, "AI response: ${response.text}")
                callback?.onAiResponse(response)

                // 保存AI回复，用于后续记忆更新
                currentAiResponse = response.text ?: ""

                // 添加到对话历史
                response.text?.let { aiText ->
                    addToHistory(text, aiText)
                }

                // AI回复后，进行语音合成（带情绪）
                response.text?.let { speakText ->
                    if (speakText.isNotEmpty()) {
                        // 将AI的tone映射到TTS情绪
                        val ttsEmotion = EmotionMapper.mapToTtsEmotion(response.tone)
                        Log.d(TAG, "Map tone '${response.tone}' to TTS emotion '$ttsEmotion'")
                        speak(speakText, ttsEmotion)
                    } else {
                        // 没有回复内容，更新记忆并重新开始监听
                        updateMemoryAndRestart()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "AI processing error: ${e.message}")
                callback?.onError("AI", -1, e.message ?: "Unknown error")
                // 出错时，在连续模式下重新开始监听
                if (isContinuousMode) {
                    restartListening()
                }
            } finally {
                isProcessing = false
            }
        }
    }

    /**
     * 更新记忆并重新开始监听
     */
    private fun updateMemoryAndRestart() {
        // 异步更新记忆
        if (currentUserInput.isNotEmpty() && currentAiResponse.isNotEmpty()) {
            memoryManager.processConversation(currentUserInput, currentAiResponse) {
                Log.i(TAG, "Memory updated")
            }
        }
        // 重新开始监听
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
        Log.i(TAG, "Start speaking: $text, emotion: $emotion")

        ttsManager.speak(text, emotion, object : TtsManager.TtsCallback {
            override fun onSynthesisStart(text: String) {
                Log.d(TAG, "TTS synthesis start")
            }

            override fun onSynthesisComplete() {
                Log.d(TAG, "TTS synthesis complete")
            }

            override fun onPlayStart() {
                Log.d(TAG, "TTS play start")
                // 启用唇同步
                LAppLive2DManager.getInstance().setLipSyncEnabled(true)
                callback?.onAiSpeakStart(text)
            }

            override fun onPlayComplete() {
                Log.d(TAG, "TTS play complete")
                // 禁用唇同步
                LAppLive2DManager.getInstance().setLipSyncEnabled(false)
                isSpeaking = false
                callback?.onAiSpeakComplete()
                // 更新记忆并重新开始监听
                updateMemoryAndRestart()
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
        dialogValidator.close()
        conversationHistory.clear()
        // 取消所有协程
        scopeJob.cancel()
    }
}
