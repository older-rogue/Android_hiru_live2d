package com.zx.hiru.domain.usecase

import android.os.Handler
import android.os.Looper
import com.zx.live2d.LAppDefine
import com.zx.live2d.LAppLive2DManager

/**
 * 动作 UseCase
 *
 * 管理 Live2D 动作触发
 *
 * 注意：此 UseCase 依赖 live2d 模块的 LAppLive2DManager，属于基础设施依赖
 * LAppLive2DManager 是单例，必须在 Activity 启动后才能使用
 */
class MotionUseCase {
    private val live2DManager: LAppLive2DManager
        get() = LAppLive2DManager.getInstance()
    companion object {
        private const val THINKING_MOTION_INDEX = 1
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isThinkingMotionPlaying = false

    /**
     * 开始思考动作（循环播放）
     */
    fun startThinkingMotion() {
        if (isThinkingMotionPlaying) return
        isThinkingMotionPlaying = true
        playThinkingMotionLoop()
    }

    /**
     * 停止思考动作
     */
    fun stopThinkingMotion() {
        isThinkingMotionPlaying = false
    }

    /**
     * 播放情绪动作
     */
    fun playEmotionMotion(action: String) {
        val motionIndex = emotionMotionMap[action] ?: return
        live2DManager.models.firstOrNull()?.let { model ->
            if (model.modelSetting.getMotionCount(LAppDefine.MotionGroup.TAP_BODY.id) > 0) {
                model.startMotion(
                    LAppDefine.MotionGroup.TAP_BODY.id,
                    motionIndex,
                    LAppDefine.Priority.NORMAL.priority
                )
            }
        }
    }

    private fun playThinkingMotionLoop() {
        if (!isThinkingMotionPlaying) return

        val models = live2DManager.models
        if (models.isEmpty()) {
            mainHandler.postDelayed({ playThinkingMotionLoop() }, 100)
            return
        }

        val model = models[0]
        if (model.modelSetting.getMotionCount(LAppDefine.MotionGroup.TAP_BODY.id) == 0) {
            return
        }

        model.startMotion(
            LAppDefine.MotionGroup.TAP_BODY.id,
            THINKING_MOTION_INDEX,
            LAppDefine.Priority.NORMAL.priority,
            { _ ->
                if (isThinkingMotionPlaying) {
                    mainHandler.post { playThinkingMotionLoop() }
                }
            },
            null
        )
    }

    private val emotionMotionMap = mapOf(
        "shy" to 2, "bored" to 3, "innocent" to 4, "happy" to 5,
        "cute" to 6, "surprised" to 7, "excited" to 8,
        "angry" to 9, "sad" to 10, "love" to 11
    )
}
