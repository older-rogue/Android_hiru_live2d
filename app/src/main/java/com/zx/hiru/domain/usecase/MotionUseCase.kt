package com.zx.hiru.domain.usecase

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.zx.live2d.LAppDefine
import com.zx.live2d.LAppLive2DManager

/**
 * 动作 UseCase
 *
 * 管理 Live2D 动作触发
 *
 * 注意：此 UseCase 依赖 live2d 模块的 LAppLive2DManager，属于基础设施依赖
 * LAppLive2DManager 是单例，必须在 GL Surface 创建后才能使用
 */
class MotionUseCase {
    companion object {
        private const val TAG = "MotionUseCase"
        private const val THINKING_MOTION_INDEX = 1
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isThinkingMotionPlaying = false

    /**
     * Live2D 是否已就绪
     * 需要 CubismFramework 已初始化 + 模型已加载完成
     */
    private val isLive2DReady: Boolean
        get() = try {
            val manager = LAppLive2DManager.getInstance()
            manager.loadState == LAppLive2DManager.LoadState.READY && manager.modelNum > 0
        } catch (e: Exception) {
            false
        }

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
        try {
            if (!isLive2DReady) return
            val manager = LAppLive2DManager.getInstance()
            val motionIndex = emotionMotionMap[action] ?: return
            manager.models.firstOrNull()?.let { model ->
                if (model.modelSetting.getMotionCount(LAppDefine.MotionGroup.TAP_BODY.id) > 0) {
                    model.startMotion(
                        LAppDefine.MotionGroup.TAP_BODY.id,
                        motionIndex,
                        LAppDefine.Priority.NORMAL.priority
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play emotion motion: ${e.message}")
        }
    }

    private fun playThinkingMotionLoop() {
        if (!isThinkingMotionPlaying) return

        try {
            if (!isLive2DReady) {
                mainHandler.postDelayed({ playThinkingMotionLoop() }, 100)
                return
            }

            val manager = LAppLive2DManager.getInstance()
            val models = manager.models
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
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play thinking motion: ${e.message}")
            if (isThinkingMotionPlaying) {
                mainHandler.postDelayed({ playThinkingMotionLoop() }, 100)
            }
        }
    }

    private val emotionMotionMap = mapOf(
        "shy" to 2, "bored" to 3, "innocent" to 4, "happy" to 5,
        "cute" to 6, "surprised" to 7, "excited" to 8,
        "angry" to 9, "sad" to 10, "love" to 11
    )
}
