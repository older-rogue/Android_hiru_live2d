package com.zx.hiru.data.service.impl

import android.content.Context
import com.zx.hiru.data.service.TtsService
import com.zx.hiru.tts.TtsManager

/**
 * TTS Service 实现
 *
 * 封装 TtsManager 单例
 * 在构造时自动设置 Context
 */
class TtsServiceImpl(
    context: Context
) : TtsService {

    private val appContext = context.applicationContext
    private val ttsManager = TtsManager.getInstance()

    init {
        ttsManager.setContext(appContext)
    }

    override fun speak(text: String, emotion: String?, callback: TtsService.TtsCallback) {
        ttsManager.speak(text, emotion, object : TtsManager.TtsCallback {
            override fun onSynthesisStart(text: String) {
                // 合成开始，暂不处理
            }

            override fun onSynthesisComplete() {
                // 合成完成，暂不处理
            }

            override fun onPlayStart() {
                callback.onPlayStart()
            }

            override fun onPlayComplete() {
                callback.onPlayComplete()
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                callback.onError(errorCode, errorMessage)
            }
        })
    }

    override fun stop() {
        ttsManager.stop()
    }

    override fun release() {
        ttsManager.release()
    }
}
