package com.zx.hiru.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bytedance.speech.speechengine.SpeechEngine
import com.bytedance.speech.speechengine.SpeechEngineDefines
import com.bytedance.speech.speechengine.SpeechEngineGenerator
import com.zx.hiru.config.AppConfig
import org.json.JSONObject
import java.util.UUID

/**
 * 语音识别工具类（单例模式）
 * 封装了引擎的初始化、识别、停止等操作，调用方无需关心生命周期管理
 */
class AsrManager private constructor() : SpeechEngine.SpeechListener {

    companion object {
        private const val TAG = "AsrManager"
        private const val MAX_RETRY_COUNT = 3

        @Volatile
        private var instance: AsrManager? = null

        fun getInstance(): AsrManager =
            instance ?: synchronized(AsrManager::class.java) {
                instance ?: AsrManager().also { instance = it }
            }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechEngine: SpeechEngine? = null
    private var engineInitialized = false
    private var engineStarted = false

    private var context: Context? = null
    private var callback: AsrCallback? = null
    private var debugPath: String? = null

    private var retryCount = MAX_RETRY_COUNT

    private val uid: String = UUID.randomUUID().toString()

    /**
     * 识别结果回调接口
     */
    interface AsrCallback {
        /**
         * 识别结果回调
         * @param text 识别文本
         * @param isFinal 是否为最终结果
         */
        fun onResult(text: String, isFinal: Boolean)

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

                setListener(this@AsrManager)
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
            SpeechEngineDefines.ASR_ENGINE
        )

        // 【可选配置】Debug & Log
        debugPath?.takeIf { it.isNotEmpty() }?.let {
            setOptionString(SpeechEngineDefines.PARAMS_KEY_DEBUG_PATH_STRING, it)
        }
        setOptionString(
            SpeechEngineDefines.PARAMS_KEY_LOG_LEVEL_STRING,
            SpeechEngineDefines.LOG_LEVEL_DEBUG
        )

        // 【可选配置】User ID
        uid.takeIf { it.isNotEmpty() }?.let {
            setOptionString(SpeechEngineDefines.PARAMS_KEY_UID_STRING, it)
        }

        // 【必需配置】音频来源
        setOptionString(
            SpeechEngineDefines.PARAMS_KEY_RECORDER_TYPE_STRING,
            SpeechEngineDefines.RECORDER_TYPE_RECORDER
        )

        // 【可选配置】音频采样率
        setOptionInt(SpeechEngineDefines.PARAMS_KEY_SAMPLE_RATE_INT, 16000)
        // 【可选配置】音频通道数
        setOptionInt(SpeechEngineDefines.PARAMS_KEY_CHANNEL_NUM_INT, 1)
        setOptionInt(SpeechEngineDefines.PARAMS_KEY_UP_CHANNEL_NUM_INT, 1)

        // 【必需配置】识别服务配置
        setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_ADDRESS_STRING, AppConfig.Asr.address)
        setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_URI_STRING, AppConfig.Asr.uri)
        setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_ID_STRING, AppConfig.Asr.appId)
        setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_TOKEN_STRING, AppConfig.Asr.token)
        setOptionString(SpeechEngineDefines.PARAMS_KEY_RESOURCE_ID_STRING, AppConfig.Asr.resourceId)

        // 【必需配置】协议类型
        setOptionInt(
            SpeechEngineDefines.PARAMS_KEY_PROTOCOL_TYPE_INT,
            SpeechEngineDefines.PROTOCOL_TYPE_SEED
        )

        // 【可选配置】超时设置
        setOptionInt(SpeechEngineDefines.PARAMS_KEY_ASR_CONN_TIMEOUT_INT, 3000)
        setOptionInt(SpeechEngineDefines.PARAMS_KEY_ASR_RECV_TIMEOUT_INT, 5000)
    }

    /**
     * 配置启动参数
     */
    private fun SpeechEngine.configStartParams() {
        // 【可选配置】开启顺滑(DDC)
        setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_ENABLE_DDC_BOOL, true)
        // 【可选配置】开启文字转数字(ITN)
        setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_ENABLE_ITN_BOOL, true)
        // 【可选配置】开启标点
        setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_SHOW_PUNC_BOOL, true)
        // 【可选配置】最大语音时长
        setOptionInt(SpeechEngineDefines.PARAMS_KEY_VAD_MAX_SPEECH_DURATION_INT, 60000)
        // 【可选配置】直接传递自定义的ASR请求JSON Request，若使用此参数需自行确保JSON格式正确
        val resParams = JSONObject()
        resParams.put("enable_nonstream", true)
        resParams.put("end_window_size", 200)
        setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_REQ_PARAMS_STRING, resParams.toString())
    }

    /**
     * 开始识别
     * @param callback 识别回调
     */
    fun startRecognition(callback: AsrCallback) {
        partialText = ""
        this.callback = callback

        if (!engineInitialized) {
            initEngine()
            if (!engineInitialized) {
                return
            }
        }

        if (engineStarted) {
            Log.w(TAG, "Recognition already started")
            return
        }

        try {
            speechEngine?.apply {
                configStartParams()

                // 先同步停止
                val ret = sendDirective(SpeechEngineDefines.DIRECTIVE_SYNC_STOP_ENGINE, "")
                if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                    Log.w(TAG, "Sync stop failed: $ret")
                }

                // 启动引擎
                Log.i(TAG, "Starting recognition")
                val startRet = sendDirective(SpeechEngineDefines.DIRECTIVE_START_ENGINE, "")
                when {
                    startRet == SpeechEngineDefines.ERR_REC_CHECK_ENVIRONMENT_FAILED -> {
                        notifyError(startRet, "Recording permission not granted")
                    }
                    startRet != SpeechEngineDefines.ERR_NO_ERROR -> {
                        notifyError(startRet, "Start recognition failed: $startRet")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Start recognition exception: ${e.message}")
            notifyError(-1, "Start recognition exception: ${e.message}")
        }
    }

    /**
     * 停止识别
     */
    fun stopRecognition() {
        if (!engineInitialized || speechEngine == null) {
            Log.w(TAG, "Engine not initialized")
            return
        }

        if (!engineStarted) {
            Log.w(TAG, "Recognition not started")
            return
        }

        try {
            Log.i(TAG, "Stopping recognition")
            // 结束用户音频输入
            speechEngine?.sendDirective(SpeechEngineDefines.DIRECTIVE_FINISH_TALKING, "")
        } catch (e: Exception) {
            Log.e(TAG, "Stop recognition exception: ${e.message}")
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
    }

    /**
     * 重置引擎（配置变更时调用）
     * 释放旧引擎，下次使用时会用新配置重新初始化
     */
    @Synchronized
    fun reset() {
        Log.i(TAG, "Resetting ASR engine for config change")
        release()
    }

    private var partialText = ""

    override fun onSpeechMessage(type: Int, data: ByteArray, len: Int) {
        val stdData = String(data)
        when (type) {
            SpeechEngineDefines.MESSAGE_TYPE_ENGINE_START -> {
                Log.i(TAG, "Engine started")
                engineStarted = true
            }

            SpeechEngineDefines.MESSAGE_TYPE_ENGINE_STOP -> {
                Log.i(TAG, "Engine stopped")
                engineStarted = false
            }

            SpeechEngineDefines.MESSAGE_TYPE_ENGINE_ERROR -> {
                Log.e(TAG, "Engine error: $stdData")
                if (JSONObject(stdData).has("err_code") && JSONObject(stdData).get("err_code") == 4001) {
                    parseAndNotifyResult(partialText, true)
                } else {
                    parseAndNotifyError(stdData)
                }
            }

            SpeechEngineDefines.MESSAGE_TYPE_PARTIAL_RESULT -> {
                partialText = stdData
                Log.d(TAG, "Partial result: $stdData")
                parseAndNotifyResult(stdData, false)
            }

            SpeechEngineDefines.MESSAGE_TYPE_FINAL_RESULT -> {
                Log.i(TAG, "Final result: $stdData")
                parseAndNotifyResult(stdData, true)
            }

            SpeechEngineDefines.MESSAGE_TYPE_CONNECTION_CONNECTED -> {
                Log.i(TAG, "Connection connected")
            }

            SpeechEngineDefines.MESSAGE_TYPE_VOLUME_LEVEL -> {
                // 音量回调，暂不处理
                Log.i(TAG, "音量回调: $data")
            }
        }
    }

    /**
     * 解析并通知识别结果
     */
    private fun parseAndNotifyResult(data: String, isFinal: Boolean) {
        try {
            val json = JSONObject(data)
            var result = ""

            // 尝试解析结果文本，根据实际返回格式调整
            result = when {
                json.has("result") -> json.getString("result")
                json.has("text") -> json.getString("text")
                else -> data
            }

            mainHandler.post {
                callback?.onResult(result, isFinal)
            }

        } catch (e: Exception) {
            // 解析失败，直接返回原始数据
            mainHandler.post {
                callback?.onResult(data, isFinal)
            }
        }
    }

    /**
     * 解析并通知错误
     */
    private fun parseAndNotifyError(data: String) {
        try {
            val json = JSONObject(data)
            val code = json.optInt("err_code", -1)
            val message = json.optString("err_msg", data)

            notifyError(code, message)

        } catch (e: Exception) {
            notifyError(-1, data)
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
