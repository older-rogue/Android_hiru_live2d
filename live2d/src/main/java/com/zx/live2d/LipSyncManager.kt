package com.zx.live2d

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * 唇同步管理器（单例模式）
 *
 * 管理 TTS 音频音量，为 Live2D 模型提供嘴部开合值
 * 使用滑动平均平滑音量，避免嘴部抖动
 * 支持超时衰减，无数据更新时嘴型自动关闭
 */
class LipSyncManager private constructor() {

    companion object {
        private const val TAG = "LipSyncManager"
        private const val SMOOTHING_WINDOW_SIZE = 5
        private const val VOLUME_MULTIPLIER = 2.0f  // 放大音量以获得更明显的嘴部动作
        private const val MIN_THRESHOLD = 0.05f     // 最小阈值，过滤噪音
        private const val DECAY_TIMEOUT_MS = 50L    // 超时时间，超过此时间无更新则开始衰减
        private const val DECAY_RATE = 0.3f         // 每帧衰减比例

        @Volatile
        private var instance: LipSyncManager? = null

        @JvmStatic
        fun getInstance(): LipSyncManager {
            return instance ?: synchronized(LipSyncManager::class.java) {
                instance ?: LipSyncManager().also { instance = it }
            }
        }
    }

    // 当前音量值（线程安全）
    private var currentVolume = 0.0f

    // 最后一次更新时间
    private var lastUpdateTime = 0L

    // 历史音量值（用于平滑）
    private val volumeHistory = FloatArray(SMOOTHING_WINDOW_SIZE)
    private var historyIndex = 0
    private var historyCount = 0

    // 唇同步是否启用
    private val lipSyncEnabled = AtomicBoolean(false)

    /**
     * 设置原始音量值
     * @param volume 音量值（0-1）
     */
    fun setVolume(volume: Float) {
        if (!lipSyncEnabled.get()) return

        synchronized(this) {
            // 更新时间戳
            lastUpdateTime = System.currentTimeMillis()

            // 存入历史记录
            volumeHistory[historyIndex] = volume
            historyIndex = (historyIndex + 1) % SMOOTHING_WINDOW_SIZE
            if (historyCount < SMOOTHING_WINDOW_SIZE) {
                historyCount++
            }

            // 计算滑动平均
            var sum = 0.0f
            for (i in 0 until historyCount) {
                sum += volumeHistory[i]
            }
            val smoothed = sum / historyCount

            // 应用放大和阈值
            currentVolume = if (smoothed < MIN_THRESHOLD) {
                0.0f
            } else {
                (smoothed * VOLUME_MULTIPLIER).coerceIn(0.0f, 1.0f)
            }
        }
    }

    /**
     * 获取平滑后的嘴部开合值
     * 如果超时无更新，则逐渐衰减到 0
     * @return 嘴部开合值（0-1）
     */
    fun getMouthValue(): Float {
        if (!lipSyncEnabled.get()) {
            return 0.0f
        }

        synchronized(this) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastUpdateTime

            // 如果超时无更新，逐渐衰减
            if (elapsed > DECAY_TIMEOUT_MS && currentVolume > 0.0f) {
                currentVolume *= (1.0f - DECAY_RATE)
                if (currentVolume < 0.01f) {
                    currentVolume = 0.0f
                }
            }

            return currentVolume
        }
    }

    /**
     * 启用唇同步
     */
    fun enable() {
        lipSyncEnabled.set(true)
        reset()
    }

    /**
     * 禁用唇同步
     */
    fun disable() {
        lipSyncEnabled.set(false)
        reset()
    }

    /**
     * 设置唇同步开关
     * @param enabled 是否启用
     */
    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            enable()
        } else {
            disable()
        }
    }

    /**
     * 是否启用
     */
    fun isEnabled(): Boolean = lipSyncEnabled.get()

    /**
     * 重置状态
     */
    private fun reset() {
        synchronized(this) {
            currentVolume = 0.0f
            lastUpdateTime = 0L
            historyIndex = 0
            historyCount = 0
            volumeHistory.fill(0.0f)
        }
    }

    /**
     * 计算 PCM 音频数据的 RMS 音量
     * @param audioData PCM 数据（16位有符号小端序）
     * @return 音量值（0-1）
     */
    fun calculateRms(audioData: ByteArray?): Float {
        if (audioData == null || audioData.size < 2) {
            return 0.0f
        }

        var sum = 0.0
        val sampleCount = audioData.size / 2

        for (i in 0 until sampleCount) {
            val byteIndex = i * 2
            // 16位有符号小端序
            val sample = ((audioData[byteIndex].toInt() and 0xFF) or
                         (audioData[byteIndex + 1].toInt() shl 8)).toShort()
            sum += sample.toDouble() * sample.toDouble()
        }

        val rms = sqrt(sum / sampleCount)
        // 归一化到 0-1（16位最大值 32767）
        return (rms / 32767.0).coerceIn(0.0, 1.0).toFloat()
    }
}
