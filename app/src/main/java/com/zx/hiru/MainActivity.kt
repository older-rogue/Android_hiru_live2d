/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */
package com.zx.hiru

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.content.Intent
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.zx.hiru.ui.settings.SettingsActivity
import com.zx.hiru.databinding.ActivityMainBinding
import com.zx.hiru.domain.model.ChatState
import com.zx.hiru.ui.main.MainViewModel
import com.zx.live2d.GLRenderer
import com.zx.live2d.LAppDefine
import com.zx.live2d.LAppDelegate
import com.zx.live2d.LAppLive2DManager
import com.zx.live2d.LAppModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/*
 * 主Activity - 应用程序入口点
 * 负责初始化OpenGL ES环境和管理应用程序生命周期
 *
 * 采用 MVVM 架构：
 * - UI 状态由 MainViewModel 管理
 * - Activity 只负责 UI 渲染和用户交互
 */
class MainActivity : FragmentActivity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 1001
        private const val REQUEST_SETTINGS = 1002
        private const val THINKING_MOTION_INDEX = 1  // 思考动作索引
    }

    private lateinit var dataBinding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModel()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Activity创建时调用
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(dataBinding.root)

        // 设置沉浸式状态栏，内容延伸到系统栏下方
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let { controller ->
            // 设置状态栏字体为黑色（浅色状态栏）
            controller.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }

        // 使用OpenGL ES 2.0
        dataBinding.gsView.setEGLContextClientVersion(2)

        // 创建渲染器
        val glRenderer = GLRenderer()

        // 设置渲染器
        dataBinding.gsView.setRenderer(glRenderer)
        // 设置渲染模式：持续渲染（每帧都重绘）
        dataBinding.gsView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        // 观察 UI 状态
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                // 处理配置缺失
                if (state.needsConfig) {
                    showSettingsPage(mandatory = true)
                }
                // 显示错误
                state.error?.let { showError(it) }
            }
        }

        // 观察 Chat 状态用于控制动作
        lifecycleScope.launch {
            viewModel.chatState.collect { state ->
                when (state) {
                    is ChatState.Thinking -> startThinkingMotion()
                    is ChatState.Responding -> {
                        stopThinkingMotion()
                        state.response.action?.let { action ->
                            startMotion(motionMap[action] ?: -1)
                        }
                    }
                    else -> {}
                }
            }
        }

        // 设置按钮点击事件
        dataBinding.btnSettings.setOnClickListener {
            showSettingsPage(mandatory = false)
        }

        // 检查并请求录音权限
        checkAndRequestAudioPermission()
    }

    /**
     * 显示错误提示
     */
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        viewModel.clearError()
    }

    /**
     * 显示设置页面
     * @param mandatory 是否为强制模式（配置缺失时不可返回）
     */
    private fun showSettingsPage(mandatory: Boolean) {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.putExtra(SettingsActivity.EXTRA_MANDATORY, mandatory)
        startActivityForResult(intent, REQUEST_SETTINGS)
    }

    /**
     * Activity 结果回调
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_SETTINGS -> {
                if (resultCode == RESULT_OK) {
                    // 设置已保存，通知 ViewModel
                    onSettingsSaved()
                }
            }
        }
    }

    /**
     * 设置保存后重新初始化
     */
    private fun onSettingsSaved() {
        viewModel.onConfigUpdated()
    }

    /**
     * 检查并请求录音权限
     */
    private fun checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            // 已有权限，开始监听
            viewModel.startListening()
        } else {
            // 请求权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    /**
     * 权限请求结果回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "录音权限已授权", Toast.LENGTH_SHORT).show()
                    viewModel.startListening()
                } else {
                    Toast.makeText(this, "录音权限被拒绝，语音功能将无法使用", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Activity变为可见时调用
    override fun onStart() {
        super.onStart()
        // 初始化LAppDelegate
        LAppDelegate.getInstance().onStart(this)
        // 设置GLSurfaceView引用，避免library模块直接依赖app的R.id
        LAppDelegate.getInstance().setGLSurfaceView(dataBinding.gsView)
    }

    // Activity获得焦点时调用
    override fun onResume() {
        super.onResume()
        // 恢复OpenGL渲染
        dataBinding.gsView.onResume()
        // 检查 Live2D 是否准备好
        checkLive2DReady()
    }

    /**
     * 检查 Live2D 是否准备好
     * 模型加载需要时间，需要延迟检查
     */
    private fun checkLive2DReady() {
        dataBinding.gsView.queueEvent {
            try {
                val manager = LAppLive2DManager.getInstance()
                if (manager.modelNum > 0) {
                    mainHandler.post {
                        isLive2DReady = true
                    }
                } else {
                    // 模型未加载，延迟重试
                    mainHandler.postDelayed({ checkLive2DReady() }, 100)
                }
            } catch (e: Exception) {
                // 初始化未完成，延迟重试
                mainHandler.postDelayed({ checkLive2DReady() }, 100)
            }
        }
    }

    // Activity失去焦点时调用
    override fun onPause() {
        super.onPause()
        // 暂停OpenGL渲染
        dataBinding.gsView.onPause()
        LAppDelegate.getInstance().onPause()
    }

    // Activity不可见时调用
    override fun onStop() {
        super.onStop()
        // 释放资源
        LAppDelegate.getInstance().onStop()
        // 重置 Live2D 状态
        isLive2DReady = false
        isThinkingMotionPlaying = false
    }

    // Activity销毁时调用
    override fun onDestroy() {
        super.onDestroy()
        // 销毁单例
        LAppDelegate.getInstance().onDestroy()
    }

    // 处理触摸事件 - 将触摸事件转发到OpenGL渲染线程
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 获取触摸坐标
        val pointX: Float = event.x
        val pointY: Float = event.y

        // 将事件放入渲染队列（在OpenGL线程中执行）
        dataBinding.gsView.queueEvent {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> LAppDelegate.getInstance()
                    .onTouchBegan(pointX, pointY)

                MotionEvent.ACTION_UP -> LAppDelegate.getInstance()
                    .onTouchEnd(pointX, pointY)

                MotionEvent.ACTION_MOVE -> LAppDelegate.getInstance()
                    .onTouchMoved(pointX, pointY)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startMotion(count: Int) {
        if (count == -1) return
        if (!isLive2DReady) return

        val models: MutableList<LAppModel> = LAppLive2DManager.getInstance().models
        if (models.isEmpty()) return

        // 遍历模型播放动作
        for (i in models.indices) {
            if (models[i].modelSetting.getMotionCount(LAppDefine.MotionGroup.TAP_BODY.id) == 0) {
                return
            }
            models[i].startMotion(
                LAppDefine.MotionGroup.TAP_BODY.id,
                count,
                LAppDefine.Priority.NORMAL.priority
            )
        }
    }

    private val motionMap = mapOf<String, Int>(
        "shy" to 2,
        "bored" to 3,
        "innocent" to 4,
        "happy" to 5,
        "cute" to 6,
        "surprised" to 7,
        "excited" to 8,
        "angry" to 9,
        "sad" to 10,
        "love" to 11
    )

    // 思考动作是否正在播放
    private var isThinkingMotionPlaying = false

    // Live2D 是否已准备好
    private var isLive2DReady = false

    /**
     * 开始播放思考动作（循环）
     */
    private fun startThinkingMotion() {
        if (isThinkingMotionPlaying) return
        if (!isLive2DReady) return  // Live2D 未准备好，直接返回
        isThinkingMotionPlaying = true
        playThinkingMotionLoop()
    }

    /**
     * 循环播放思考动作
     */
    private fun playThinkingMotionLoop() {
        if (!isThinkingMotionPlaying) return
        if (!isLive2DReady) {
            isThinkingMotionPlaying = false
            return
        }

        val models = LAppLive2DManager.getInstance().models
        if (models.isEmpty()) {
            // 模型未加载，延迟重试
            mainHandler.postDelayed({ playThinkingMotionLoop() }, 100)
            return
        }

        val model = models[0]
        if (model.modelSetting.getMotionCount(LAppDefine.MotionGroup.TAP_BODY.id) == 0) {
            return
        }

        // 播放思考动作，结束后继续播放（循环）
        model.startMotion(
            LAppDefine.MotionGroup.TAP_BODY.id,
            THINKING_MOTION_INDEX,
            LAppDefine.Priority.NORMAL.priority,
            { _ ->
                // 动作结束，如果还在思考状态则继续播放
                if (isThinkingMotionPlaying && isLive2DReady) {
                    mainHandler.post { playThinkingMotionLoop() }
                }
            },
            null
        )
    }

    /**
     * 停止思考动作
     */
    private fun stopThinkingMotion() {
        isThinkingMotionPlaying = false
    }
}
