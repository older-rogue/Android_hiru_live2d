package com.zx.hiru.config

data class RuntimeApiConfig(
    val ai: Ai = Ai(),
    val asr: Asr = Asr(),
    val tts: Tts = Tts()
) {
    data class Ai(
        val baseUrl: String = "",
        val apiKey: String = "",
        val model: String = "",
        val timeoutMs: String = "",
        val systemPrompt: String = ""
    )

    data class Asr(
        val appId: String = "",
        val token: String = "",
        val address: String = "",
        val uri: String = "",
        val resourceId: String = ""
    )

    data class Tts(
        val appId: String = "",
        val token: String = "",
        val address: String = "",
        val uri: String = "",
        val resourceId: String = ""
    )

    /**
     * 检查关键配置是否完整
     * 关键配置：AI(baseUrl, apiKey, model), ASR(全部), TTS(全部)
     * timeoutMs 和 systemPrompt 允许为空
     */
    fun isComplete(): Boolean {
        return ai.baseUrl.isNotBlank() &&
            ai.apiKey.isNotBlank() &&
            ai.model.isNotBlank() &&
            asr.appId.isNotBlank() &&
            asr.token.isNotBlank() &&
            asr.address.isNotBlank() &&
            asr.uri.isNotBlank() &&
            asr.resourceId.isNotBlank() &&
            tts.appId.isNotBlank() &&
            tts.token.isNotBlank() &&
            tts.address.isNotBlank() &&
            tts.uri.isNotBlank() &&
            tts.resourceId.isNotBlank()
    }
}
