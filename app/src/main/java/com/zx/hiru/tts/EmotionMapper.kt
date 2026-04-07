package com.zx.hiru.tts

/**
 * 情绪映射工具
 *
 * 将大模型返回的情绪(tone)映射到TTS支持的情绪
 *
 * TTS支持的中文音色情绪：
 * - happy: 开心
 * - sad: 悲伤
 * - angry: 生气
 * - surprised: 惊讶
 * - fear: 恐惧
 * - hate: 厌恶
 * - excited: 激动
 * - coldness: 冷漠
 * - neutral: 中性
 * - depressed: 沮丧
 * - lovey-dovey: 撒娇
 * - shy: 害羞
 * - comfort: 安慰鼓励
 * - tension: 咆哮/焦急
 * - tender: 温柔
 * - storytelling: 讲故事/自然讲述
 * - radio: 情感电台
 * - magnetic: 磁性
 * - advertising: 广告营销
 * - vocal-fry: 气泡音
 * - asmr: 低语(ASMR)
 * - news: 新闻播报
 * - entertainment: 娱乐八卦
 * - dialect: 方言
 */
object EmotionMapper {

    /**
     * 大模型情绪到TTS情绪的映射
     *
     * 大模型支持的tone: happy|shy|curious|surprised|normal|confused|excited|sarcastic|angry|dramatic
     */
    private val toneToEmotionMap = mapOf(
        // 直接匹配
        "happy" to "happy",
        "sad" to "sad",
        "angry" to "angry",
        "surprised" to "surprised",
        "excited" to "excited",
        "shy" to "shy",

        // 近似映射
        "curious" to "surprised",      // 好奇 -> 惊讶
        "confused" to "neutral",       // 困惑 -> 中性
        "normal" to "neutral",         // 正常 -> 中性
        "sarcastic" to "coldness",     // 讽刺 -> 冷漠
        "dramatic" to "excited",       // 戏剧性 -> 激动

        // 额外的大模型情绪映射
        "bored" to "coldness",         // 无聊 -> 冷漠
        "innocent" to "shy",           // 天真 -> 害羞
        "cute" to "lovey-dovey",       // 可爱 -> 撒娇
        "love" to "lovey-dovey",       // 爱 -> 撒娇
        "fear" to "fear",              // 恐惧
        "hate" to "hate",              // 厌恶
        "depressed" to "depressed",    // 沮丧
        "comfort" to "comfort",        // 安慰
        "tension" to "tension",        // 焦急
        "tender" to "tender",          // 温柔
    )

    /**
     * 将大模型的tone映射到TTS的emotion
     *
     * @param tone 大模型返回的情绪，如 "happy", "shy", "angry" 等
     * @return TTS支持的情绪，如果无法映射则返回 "neutral"
     */
    fun mapToTtsEmotion(tone: String?): String {
        if (tone.isNullOrEmpty()) {
            return "neutral"
        }

        val normalizedTone = tone.lowercase().trim()
        return toneToEmotionMap[normalizedTone] ?: "neutral"
    }

    /**
     * 根据情绪强度获取emotion_scale
     *
     * @param intensity 强度描述，如 "low", "medium", "high" 或数字字符串
     * @return emotion_scale值，范围1-5
     */
    fun mapToEmotionScale(intensity: String?): Int {
        if (intensity.isNullOrEmpty()) {
            return 4 // 默认值
        }

        return when (intensity.lowercase().trim()) {
            "low", "weak", "1" -> 2
            "medium", "normal", "2", "3" -> 3
            "high", "strong", "4" -> 4
            "very_high", "very_strong", "5" -> 5
            else -> {
                // 尝试解析数字
                intensity.toIntOrNull()?.coerceIn(1, 5) ?: 4
            }
        }
    }

    /**
     * 获取所有支持的TTS情绪列表
     */
    fun getSupportedTtsEmotions(): List<String> {
        return listOf(
            "happy", "sad", "angry", "surprised", "fear", "hate",
            "excited", "coldness", "neutral", "depressed", "lovey-dovey",
            "shy", "comfort", "tension", "tender", "storytelling",
            "radio", "magnetic", "advertising", "vocal-fry", "asmr",
            "news", "entertainment", "dialect"
        )
    }

    /**
     * 检查情绪是否是TTS直接支持的
     */
    fun isTtsEmotionSupported(emotion: String?): Boolean {
        if (emotion.isNullOrEmpty()) return false
        return getSupportedTtsEmotions().contains(emotion.lowercase().trim())
    }
}
