package com.zx.hiru.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bytedance.speech.speechengine.SpeechEngine
import com.bytedance.speech.speechengine.SpeechEngineDefines
import com.bytedance.speech.speechengine.SpeechEngineGenerator
import com.zx.hiru.config.AppConfig
import com.zx.live2d.LipSyncManager
import org.json.JSONObject

/**
 * 语音合成工具类（单例模式）
 * 封装了引擎的初始化、合成、播放等操作，调用方无需关心生命周期管理
 */
class TtsManager private constructor() : SpeechEngine.SpeechListener {

    companion object {
        private const val TAG = "TtsManager"
        private const val MAX_RETRY_COUNT = 3

        @Volatile
        private var instance: TtsManager? = null

        fun getInstance(): TtsManager =
            instance ?: synchronized(TtsManager::class.java) {
                instance ?: TtsManager().also { instance = it }
            }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechEngine: SpeechEngine? = null
    private var engineInitialized = false
    private var engineStarted = false

    private var context: Context? = null
    private var callback: TtsCallback? = null
    private var debugPath: String? = null

    private var retryCount = MAX_RETRY_COUNT

    // 待合成的文本
    private var pendingText: String? = null

    // 待合成的情绪
    private var pendingEmotion: String? = null
    private var pendingEmotionScale: Int = 4

    private val startPayload: String = "{\n" +
            "  \"req_params\": {\n" +
            "    \"speaker\": \"saturn_zh_female_keainvsheng_tob\",\n" +
            "    \"audio_params\": {\n" +
            "      \"speech_rate\": 0,\n" +
            "      \"sample_rate\": 44100,\n" +
            "      \"bit_rate\": 128000\n" +
            "    }\n" +
            "  }\n" +
            "}"

    /**
     * 语音合成回调接口
     */
    interface TtsCallback {
        /**
         * 合成开始回调
         * @param text 正在合成的文本
         */
        fun onSynthesisStart(text: String)

        /**
         * 合成完成回调
         */
        fun onSynthesisComplete()

        /**
         * 播放开始回调
         */
        fun onPlayStart()

        /**
         * 播放完成回调
         */
        fun onPlayComplete()

        /**
         * 错误回调
         * @param errorCode 错误码
         * @param errorMessage 错误信息
         */
        fun onError(errorCode: Int, errorMessage: String)
    }

    /**
     * 设置上下文
     * @param context Application Context
     */
    fun setContext(context: Context) {
        this.context = context.applicationContext
    }

    /**
     * 设置调试路径
     * @param debugPath 调试文件保存路径
     */
    fun setDebugPath(debugPath: String?) {
        this.debugPath = debugPath
    }

    /**
     * 初始化引擎（内部调用）
     */
    @Synchronized
    private fun initEngine() {
        if (engineInitialized) {
            Log.d(TAG, "Engine already initialized")
            return
        }

        if (context == null) {
            Log.e(TAG, "Context is null, please call setContext first")
            notifyError(-1, "Context is null")
            return
        }

        try {
            Log.i(TAG, "Creating speech engine")
            speechEngine = SpeechEngineGenerator.getInstance().apply {
                createEngine()
                setContext(context!!)

                // 配置初始化参数
                configInitParams()

                Log.i(TAG, "Initializing engine")
                val ret = initEngine()
                if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                    val errMsg = "Engine init failed, code: $ret"
                    Log.e(TAG, errMsg)
                    notifyError(ret, errMsg)
                    return
                }

                setListener(this@TtsManager)
            }
            engineInitialized = true
            Log.i(TAG, "Engine initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Init engine exception: ${e.message}")
            notifyError(-1, "Init engine exception: ${e.message}")
        }
    }

    /**
     * 配置初始化参数
     */
    private fun SpeechEngine.configInitParams() {
        // 【必需配置】Engine Name
        setOptionString(
            SpeechEngineDefines.PARAMS_KEY_ENGINE_NAME_STRING,
            SpeechEngineDefines.BITTS_ENGINE
        )

        // 【必需配置】在线合成鉴权
        setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_ID_STRING, AppConfig.Tts.appId)
        setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_TOKEN_STRING, AppConfig.Tts.token)

        // 【必需配置】服务地址
        setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_ADDRESS_STRING, AppConfig.Tts.address)
        setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_URI_STRING, AppConfig.Tts.uri)
        setOptionString(SpeechEngineDefines.PARAMS_KEY_RESOURCE_ID_STRING, AppConfig.Tts.resourceId)

        // 【必需配置】启动参数
        startPayload.takeIf { it.isNotEmpty() }?.let {
            setOptionString(SpeechEngineDefines.PARAMS_KEY_START_ENGINE_PAYLOAD_STRING, it)
        }

        // 【可选配置】连接超时
        setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_CONN_TIMEOUT_INT, 3000)
        //【可选配置】是否打开播放器的数据回调（启用以支持唇同步）
        setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ENABLE_PLAYER_AUDIO_CALLBACK_BOOL, true)

    }

    /**
     * 开始说话（语音合成）
     * @param text 要合成的文本
     */
    fun speak(text: String) {
        speak(text, null, null)
    }

    /**
     * 开始说话（语音合成）
     * @param text 要合成的文本
     * @param callback 合成回调
     */
    fun speak(text: String, callback: TtsCallback?) {
        speak(text, null, callback)
    }

    /**
     * 开始说话（语音合成，带情绪）
     * @param text 要合成的文本
     * @param emotion 情绪类型，如 "happy", "sad", "angry" 等
     * @param callback 合成回调
     */
    fun speak(text: String, emotion: String?, callback: TtsCallback?) {
        speak(text, emotion, 4, callback)
    }

    /**
     * 开始说话（语音合成，带情绪和情绪强度）
     * @param text 要合成的文本
     * @param emotion 情绪类型，如 "happy", "sad", "angry" 等
     * @param emotionScale 情绪强度，范围1-5，默认4
     * @param callback 合成回调
     */
    fun speak(text: String, emotion: String?, emotionScale: Int, callback: TtsCallback?) {
        this.callback = callback

        if (text.trim().isEmpty()) {
            notifyError(-1, "Text is empty")
            return
        }

        if (!engineInitialized) {
            initEngine()
            if (!engineInitialized) {
                return
            }
        }

        // 如果引擎未启动，先启动引擎
        if (!engineStarted) {
            startEngineAndSynthesis(text, emotion, emotionScale.coerceIn(1, 5))
        } else {
            // 引擎已启动，直接合成
            synthesis(text, emotion, emotionScale.coerceIn(1, 5))
        }
    }

    /**
     * 启动引擎并合成
     */
    private fun startEngineAndSynthesis(text: String, emotion: String? = null, emotionScale: Int = 4) {
        try {
            speechEngine?.apply {
                // 先同步停止
                val ret = sendDirective(SpeechEngineDefines.DIRECTIVE_SYNC_STOP_ENGINE, "")
                if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                    Log.w(TAG, "Sync stop failed: $ret")
                }

                // 启动引擎
                Log.i(TAG, "Starting engine")
                val startRet = sendDirective(SpeechEngineDefines.DIRECTIVE_START_ENGINE, startPayload)
                if (startRet != SpeechEngineDefines.ERR_NO_ERROR) {
                    notifyError(startRet, "Start engine failed: $startRet")
                } else {
                    // 引擎启动成功后会通过回调触发合成
                    pendingText = text
                    pendingEmotion = emotion
                    pendingEmotionScale = emotionScale
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Start engine exception: ${e.message}")
            notifyError(-1, "Start engine exception: ${e.message}")
        }
    }

    /**
     * 执行合成
     */
    private fun synthesis(text: String, emotion: String? = null, emotionScale: Int = 4) {
        try {
            speechEngine?.apply {
                Log.i(TAG, "Synthesis text: $text, emotion: $emotion, scale: $emotionScale")

                // 开始会话
                var ret = sendDirective(SpeechEngineDefines.DIRECTIVE_EVENT_START_SESSION, "")
                if (ret != 0) {
                    Log.e(TAG, "Start session failed: $ret")
                    notifyError(ret, "Start session failed")
                    return
                }

                // 构建合成请求，包含情绪参数
                val result = buildSynthesisRequest(text, emotion, emotionScale)
                ret = sendDirective(SpeechEngineDefines.DIRECTIVE_EVENT_TASK_REQUEST, result)
                if (ret != 0) {
                    Log.e(TAG, "Task request failed: $ret")
                    notifyError(ret, "Synthesis request failed")
                    return
                }

                // 结束会话
                ret = sendDirective(SpeechEngineDefines.DIRECTIVE_EVENT_FINISH_SESSION, "")
                if (ret != 0) {
                    Log.e(TAG, "Finish session failed: $ret")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis exception: ${e.message}")
            notifyError(-1, "Synthesis exception: ${e.message}")
        }
    }

    /**
     * 构建合成请求JSON
     */
    private fun buildSynthesisRequest(text: String, emotion: String?, emotionScale: Int): String {
        return if (emotion != null && emotion.isNotEmpty()) {
            """{"req_params":{"text":"$text","audio_params":{"emotion":"$emotion","emotion_scale":$emotionScale}}}"""
        } else {
            """{"req_params":{"text":"$text"}}"""
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        if (!engineInitialized || speechEngine == null) {
            return
        }

        try {
            Log.i(TAG, "Stopping engine")
            speechEngine?.sendDirective(SpeechEngineDefines.DIRECTIVE_STOP_ENGINE, "")
        } catch (e: Exception) {
            Log.e(TAG, "Stop exception: ${e.message}")
        }
    }

    /**
     * 暂停播放
     */
    fun pause() {
        if (!engineInitialized || speechEngine == null || !engineStarted) {
            return
        }

        try {
            speechEngine?.sendDirective(SpeechEngineDefines.DIRECTIVE_PAUSE_PLAYER, "")
        } catch (e: Exception) {
            Log.e(TAG, "Pause exception: ${e.message}")
        }
    }

    /**
     * 恢复播放
     */
    fun resume() {
        if (!engineInitialized || speechEngine == null || !engineStarted) {
            return
        }

        try {
            speechEngine?.sendDirective(SpeechEngineDefines.DIRECTIVE_RESUME_PLAYER, "")
        } catch (e: Exception) {
            Log.e(TAG, "Resume exception: ${e.message}")
        }
    }

    /**
     * 释放资源
     */
    @Synchronized
    fun release() {
        speechEngine?.let {
            try {
                Log.i(TAG, "Destroying engine")
                it.destroyEngine()
            } catch (e: Exception) {
                Log.e(TAG, "Destroy engine exception: ${e.message}")
            }
        }
        speechEngine = null
        engineInitialized = false
        engineStarted = false
        callback = null
        pendingText = null
        pendingEmotion = null
        pendingEmotionScale = 4
    }

    /**
     * 重置引擎（配置变更时调用）
     * 释放旧引擎，下次使用时会用新配置重新初始化
     */
    @Synchronized
    fun reset() {
        Log.i(TAG, "Resetting TTS engine for config change")
        release()
    }

    override fun onSpeechMessage(type: Int, data: ByteArray, len: Int) {
        val stdData = String(data)

        when (type) {
            SpeechEngineDefines.MESSAGE_TYPE_ENGINE_START -> {
                Log.i(TAG, "Engine started")
                engineStarted = true
                retryCount = MAX_RETRY_COUNT
                // 引擎启动后，如果有待合成的文本，开始合成
                pendingText?.let { text ->
                    val emotion = pendingEmotion
                    val scale = pendingEmotionScale
                    pendingText = null
                    pendingEmotion = null
                    pendingEmotionScale = 4
                    synthesis(text, emotion, scale)
                }
            }

            SpeechEngineDefines.MESSAGE_TYPE_ENGINE_STOP -> {
                Log.i(TAG, "Engine stopped")
                engineStarted = false
                notifyPlayComplete()
            }

            SpeechEngineDefines.MESSAGE_TYPE_ENGINE_ERROR -> {
                Log.e(TAG, "Engine error: $stdData")
                parseAndNotifyError(stdData)
            }

            SpeechEngineDefines.MESSAGE_TYPE_EVENT_TTS_SENTENCE_START -> {
                Log.d(TAG, "Synthesis start: $stdData")
                notifySynthesisStart(stdData)
            }

            SpeechEngineDefines.MESSAGE_TYPE_EVENT_TTS_SENTENCE_END -> {
                Log.d(TAG, "Synthesis end: $stdData")
                notifySynthesisComplete()
            }

            SpeechEngineDefines.MESSAGE_TYPE_PLAYER_START_PLAY_AUDIO -> {
                Log.d(TAG, "Play start: $stdData")
                notifyPlayStart()
            }

            SpeechEngineDefines.MESSAGE_TYPE_PLAYER_FINISH_PLAY_AUDIO -> {
                Log.d(TAG, "Play finish: $stdData")
                notifyPlayComplete()
            }

            SpeechEngineDefines.MESSAGE_TYPE_EVENT_TTS_RESPONSE -> {
                Log.d(TAG, "TTS response, data length: ${stdData.length}")
            }

            SpeechEngineDefines.MESSAGE_TYPE_EVENT_TTS_ENDED -> {
                Log.d(TAG, "TTS ended: $stdData")
            }

            // 音频数据回调 - 用于唇同步
            SpeechEngineDefines.MESSAGE_TYPE_PLAYER_AUDIO_DATA -> {
                // 计算 RMS 音量并传递给 LipSyncManager
                val volume = LipSyncManager.getInstance().calculateRms(data)
                LipSyncManager.getInstance().setVolume(volume)
            }

            // 未知消息类型，记录日志用于调试
            else -> {
                // 如果数据是二进制（非文本），可能是音频数据
                if (len > 0 && !isTextData(data)) {
                    val volume = LipSyncManager.getInstance().calculateRms(data)
                    LipSyncManager.getInstance().setVolume(volume)
                }
            }
        }
    }

    /**
     * 检查数据是否为文本（用于区分音频数据和文本消息）
     */
    private fun isTextData(data: ByteArray): Boolean {
        return try {
            // 尝试解码为字符串，如果成功且大部分是可打印字符，则为文本
            val str = String(data, 0, minOf(data.size, 100), Charsets.UTF_8)
            str.all { it.isLetterOrDigit() || it.isWhitespace() || it in "{}[]\":,.-_" }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 解析并通知错误
     */
    private fun parseAndNotifyError(data: String) {
        try {
            val json = JSONObject(data)
            if (!json.has("err_code")) {
                return
            }

            val code = json.getInt("err_code")

            // 网络错误重试
            if (code == SpeechEngineDefines.CODE_CONNECT_TIMEOUT ||
                code == SpeechEngineDefines.CODE_RECEIVE_TIMEOUT ||
                code == SpeechEngineDefines.CODE_NET_LIB_ERROR
            ) {
                if (retryCount > 0) {
                    Log.w(TAG, "Network error, retrying... count: $retryCount")
                    retryCount--
                    // 这里可以添加重试逻辑
                    return
                }
            }

            val message = json.optString("err_msg", data)
            notifyError(code, message)

        } catch (e: Exception) {
            notifyError(-1, data)
        }
    }

    /**
     * 通知合成开始（线程安全）
     */
    private fun notifySynthesisStart(text: String) {
        mainHandler.post {
            callback?.onSynthesisStart(text)
        }
    }

    /**
     * 通知合成完成（线程安全）
     */
    private fun notifySynthesisComplete() {
        mainHandler.post {
            callback?.onSynthesisComplete()
        }
    }

    /**
     * 通知播放开始（线程安全）
     */
    private fun notifyPlayStart() {
        mainHandler.post {
            callback?.onPlayStart()
        }
    }

    /**
     * 通知播放完成（线程安全）
     */
    private fun notifyPlayComplete() {
        mainHandler.post {
            callback?.onPlayComplete()
        }
    }

    /**
     * 通知错误（线程安全）
     */
    private fun notifyError(code: Int, message: String) {
        mainHandler.post {
            callback?.onError(code, message)
        }
    }
}
