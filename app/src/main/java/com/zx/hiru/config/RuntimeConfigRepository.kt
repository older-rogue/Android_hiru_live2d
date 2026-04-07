package com.zx.hiru.config

import com.zx.hiru.cache.CacheKeys
import com.zx.hiru.cache.CacheManager

class RuntimeConfigRepository(
    private val storage: Storage = SharedPrefsStorage
) {
    interface Storage {
        fun getString(key: String): String
        fun putString(key: String, value: String)
    }

    fun load(): RuntimeApiConfig {
        return RuntimeApiConfig(
            ai = RuntimeApiConfig.Ai(
                baseUrl = storage.getString(CacheKeys.AI_BASE_URL),
                apiKey = storage.getString(CacheKeys.AI_API_KEY),
                model = storage.getString(CacheKeys.AI_MODEL),
                timeoutMs = storage.getString(CacheKeys.AI_TIMEOUT_MS),
                systemPrompt = storage.getString(CacheKeys.AI_SYSTEM_PROMPT)
            ),
            asr = RuntimeApiConfig.Asr(
                appId = storage.getString(CacheKeys.ASR_APP_ID),
                token = storage.getString(CacheKeys.ASR_TOKEN),
                address = storage.getString(CacheKeys.ASR_ADDRESS),
                uri = storage.getString(CacheKeys.ASR_URI),
                resourceId = storage.getString(CacheKeys.ASR_RESOURCE_ID)
            ),
            tts = RuntimeApiConfig.Tts(
                appId = storage.getString(CacheKeys.TTS_APP_ID),
                token = storage.getString(CacheKeys.TTS_TOKEN),
                address = storage.getString(CacheKeys.TTS_ADDRESS),
                uri = storage.getString(CacheKeys.TTS_URI),
                resourceId = storage.getString(CacheKeys.TTS_RESOURCE_ID)
            )
        )
    }

    fun save(config: RuntimeApiConfig) {
        storage.putString(CacheKeys.AI_BASE_URL, config.ai.baseUrl)
        storage.putString(CacheKeys.AI_API_KEY, config.ai.apiKey)
        storage.putString(CacheKeys.AI_MODEL, config.ai.model)
        storage.putString(CacheKeys.AI_TIMEOUT_MS, config.ai.timeoutMs)
        storage.putString(CacheKeys.AI_SYSTEM_PROMPT, config.ai.systemPrompt)

        storage.putString(CacheKeys.ASR_APP_ID, config.asr.appId)
        storage.putString(CacheKeys.ASR_TOKEN, config.asr.token)
        storage.putString(CacheKeys.ASR_ADDRESS, config.asr.address)
        storage.putString(CacheKeys.ASR_URI, config.asr.uri)
        storage.putString(CacheKeys.ASR_RESOURCE_ID, config.asr.resourceId)

        storage.putString(CacheKeys.TTS_APP_ID, config.tts.appId)
        storage.putString(CacheKeys.TTS_TOKEN, config.tts.token)
        storage.putString(CacheKeys.TTS_ADDRESS, config.tts.address)
        storage.putString(CacheKeys.TTS_URI, config.tts.uri)
        storage.putString(CacheKeys.TTS_RESOURCE_ID, config.tts.resourceId)
    }

    private object SharedPrefsStorage : Storage {
        override fun getString(key: String): String = CacheManager.getUserInfo(key)

        override fun putString(key: String, value: String) {
            CacheManager.saveUserInfo(key, value)
        }
    }
}
