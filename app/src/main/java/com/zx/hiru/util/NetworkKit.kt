package com.zx.hiru.util

import com.zx.hiru.config.AppConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 网络工具类
 *
 * 提供共享的网络客户端和 JSON 解析器配置
 */
object NetworkKit {

    /**
     * 共享的 JSON 解析器配置
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * JSON 媒体类型
     */
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 创建 HTTP 客户端
     *
     * @param timeoutMs 超时时间（毫秒）
     * @return 配置好的 OkHttpClient 实例
     */
    fun createHttpClient(timeoutMs: Long = AppConfig.Ai.timeoutMs): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
}
