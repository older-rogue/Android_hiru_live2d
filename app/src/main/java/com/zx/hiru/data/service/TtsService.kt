package com.zx.hiru.data.service

/**
 * 语音合成服务接口
 */
interface TtsService {
    /**
     * 开始语音合成
     */
    fun speak(text: String, emotion: String?, callback: TtsCallback)

    /**
     * 停止播放
     */
    fun stop()

    /**
     * 释放资源
     */
    fun release()

    /**
     * 语音合成回调
     */
    interface TtsCallback {
        fun onPlayStart()
        fun onPlayComplete()
        fun onError(code: Int, message: String)
    }
}
