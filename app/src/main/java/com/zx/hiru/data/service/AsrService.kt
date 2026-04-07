package com.zx.hiru.data.service

/**
 * 语音识别服务接口
 */
interface AsrService {
    /**
     * 开始语音识别
     */
    fun startRecognition(callback: AsrCallback)

    /**
     * 停止语音识别
     */
    fun stopRecognition()

    /**
     * 释放资源
     */
    fun release()

    /**
     * 语音识别回调
     */
    interface AsrCallback {
        fun onResult(text: String, isFinal: Boolean)
        fun onError(code: Int, message: String)
    }
}
