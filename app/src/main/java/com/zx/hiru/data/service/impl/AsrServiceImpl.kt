package com.zx.hiru.data.service.impl

import android.content.Context
import com.zx.hiru.data.service.AsrService
import com.zx.hiru.tts.AsrManager

/**
 * ASR Service 实现
 *
 * 封装 AsrManager 单例
 * 在构造时自动设置 Context
 */
class AsrServiceImpl(
    context: Context
) : AsrService {

    private val appContext = context.applicationContext
    private val asrManager = AsrManager.getInstance()

    init {
        asrManager.setContext(appContext)
    }

    override fun startRecognition(callback: AsrService.AsrCallback) {
        asrManager.startRecognition(object : AsrManager.AsrCallback {
            override fun onResult(text: String, isFinal: Boolean) {
                callback.onResult(text, isFinal)
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                callback.onError(errorCode, errorMessage)
            }
        })
    }

    override fun stopRecognition() {
        asrManager.stopRecognition()
    }

    override fun release() {
        asrManager.release()
    }
}
