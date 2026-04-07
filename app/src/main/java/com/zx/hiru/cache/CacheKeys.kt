package com.zx.hiru.cache

/**
 * 缓存键常量定义
 *
 * 定义应用中使用的缓存键，避免硬编码字符串
 */
object CacheKeys {
    // 用户信息相关
    const val USER_ID = "user_id"
    const val USER_NAME = "user_name"
    const val USER_AVATAR = "user_avatar"
    const val USER_TOKEN = "user_token"
    const val USER_EMAIL = "user_email"
    const val USER_PHONE = "user_phone"

    // 应用设置相关
    const val APP_THEME = "app_theme"
    const val APP_LANGUAGE = "app_language"
    const val APP_FIRST_LAUNCH = "app_first_launch"

    // AI配置相关
    const val AI_API_KEY = "ai_api_key"
    const val AI_BASE_URL = "ai_base_url"
    const val AI_MODEL = "ai_model"
    const val AI_TIMEOUT_MS = "ai_timeout_ms"
    const val AI_SYSTEM_PROMPT = "ai_system_prompt"

    const val ASR_APP_ID = "asr_app_id"
    const val ASR_TOKEN = "asr_token"
    const val ASR_ADDRESS = "asr_address"
    const val ASR_URI = "asr_uri"
    const val ASR_RESOURCE_ID = "asr_resource_id"

    const val TTS_APP_ID = "tts_app_id"
    const val TTS_TOKEN = "tts_token"
    const val TTS_ADDRESS = "tts_address"
    const val TTS_URI = "tts_uri"
    const val TTS_RESOURCE_ID = "tts_resource_id"
}
