package com.zx.hiru.ui.settings

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zx.hiru.R
import com.zx.hiru.config.AppConfig
import com.zx.hiru.config.RuntimeApiConfig
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * 设置页面
 *
 * 用于配置 AI、ASR、TTS 的运行时参数
 */
class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModel()

    // AI 输入框
    private lateinit var etAiBaseUrl: EditText
    private lateinit var etAiApiKey: EditText
    private lateinit var etAiModel: EditText
    private lateinit var etAiTimeout: EditText
    private lateinit var etAiSystemPrompt: EditText

    // ASR 输入框
    private lateinit var etAsrAppId: EditText
    private lateinit var etAsrToken: EditText
    private lateinit var etAsrAddress: EditText
    private lateinit var etAsrUri: EditText
    private lateinit var etAsrResourceId: EditText

    // TTS 输入框
    private lateinit var etTtsAppId: EditText
    private lateinit var etTtsToken: EditText
    private lateinit var etTtsAddress: EditText
    private lateinit var etTtsUri: EditText
    private lateinit var etTtsResourceId: EditText

    // 是否为首次配置（配置缺失时不可返回）
    private var isMandatory: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 设置状态栏为白色背景，深色图标
        setupWhiteStatusBar()

        // 获取参数
        isMandatory = intent.getBooleanExtra(EXTRA_MANDATORY, false)

        initViews()

        // 观察 UI 状态
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                state.config?.let { config ->
                    // 更新 UI 显示配置
                    updateUiFromConfig(config)
                }
                if (state.saved) {
                    // 刷新 AppConfig 缓存
                    AppConfig.refreshConfig()
                    Toast.makeText(this@SettingsActivity, "配置已保存", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                state.error?.let { error ->
                    Toast.makeText(this@SettingsActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        }

        // 设置保存按钮
        findViewById<TextView>(R.id.btn_save).setOnClickListener {
            saveConfig()
        }
        findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            if (checkInput()) {
                finish()
            }
        }

    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        // 强制模式下不允许返回
        if (isMandatory) {
            Toast.makeText(this, "请先完成配置", Toast.LENGTH_SHORT).show()
            return
        }
        super.onBackPressed()
    }

    private fun initViews() {
        // AI
        etAiBaseUrl = findViewById(R.id.et_ai_base_url)
        etAiApiKey = findViewById(R.id.et_ai_api_key)
        etAiModel = findViewById(R.id.et_ai_model)
        etAiTimeout = findViewById(R.id.et_ai_timeout)
        etAiSystemPrompt = findViewById(R.id.et_ai_system_prompt)

        // ASR
        etAsrAppId = findViewById(R.id.et_asr_app_id)
        etAsrToken = findViewById(R.id.et_asr_token)
        etAsrAddress = findViewById(R.id.et_asr_address)
        etAsrUri = findViewById(R.id.et_asr_uri)
        etAsrResourceId = findViewById(R.id.et_asr_resource_id)

        // TTS
        etTtsAppId = findViewById(R.id.et_tts_app_id)
        etTtsToken = findViewById(R.id.et_tts_token)
        etTtsAddress = findViewById(R.id.et_tts_address)
        etTtsUri = findViewById(R.id.et_tts_uri)
        etTtsResourceId = findViewById(R.id.et_tts_resource_id)
    }

    private fun setupWhiteStatusBar() {
        window.statusBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    /**
     * 从配置对象更新 UI
     */
    private fun updateUiFromConfig(config: RuntimeApiConfig) {
        // AI
        etAiBaseUrl.setText(config.ai.baseUrl)
        etAiApiKey.setText(config.ai.apiKey)
        etAiModel.setText(config.ai.model)
        etAiTimeout.setText(config.ai.timeoutMs)
        etAiSystemPrompt.setText(config.ai.systemPrompt)

        // ASR
        etAsrAppId.setText(config.asr.appId)
        etAsrToken.setText(config.asr.token)
        etAsrAddress.setText(config.asr.address)
        etAsrUri.setText(config.asr.uri)
        etAsrResourceId.setText(config.asr.resourceId)

        // TTS
        etTtsAppId.setText(config.tts.appId)
        etTtsToken.setText(config.tts.token)
        etTtsAddress.setText(config.tts.address)
        etTtsUri.setText(config.tts.uri)
        etTtsResourceId.setText(config.tts.resourceId)
    }

    /**
     * 从 UI 构建配置对象
     */
    private fun buildConfigFromUi(): RuntimeApiConfig {
        val aiBaseUrl = etAiBaseUrl.text?.toString()?.trim() ?: ""
        val aiApiKey = etAiApiKey.text?.toString()?.trim() ?: ""
        val aiModel = etAiModel.text?.toString()?.trim() ?: ""
        val asrAppId = etAsrAppId.text?.toString()?.trim() ?: ""
        val asrToken = etAsrToken.text?.toString()?.trim() ?: ""
        val asrAddress = etAsrAddress.text?.toString()?.trim() ?: ""
        val asrUri = etAsrUri.text?.toString()?.trim() ?: ""
        val asrResourceId = etAsrResourceId.text?.toString()?.trim() ?: ""
        val ttsAppId = etTtsAppId.text?.toString()?.trim() ?: ""
        val ttsToken = etTtsToken.text?.toString()?.trim() ?: ""
        val ttsAddress = etTtsAddress.text?.toString()?.trim() ?: ""
        val ttsUri = etTtsUri.text?.toString()?.trim() ?: ""
        val ttsResourceId = etTtsResourceId.text?.toString()?.trim() ?: ""

        return RuntimeApiConfig(
            ai = RuntimeApiConfig.Ai(
                baseUrl = aiBaseUrl,
                apiKey = aiApiKey,
                model = aiModel,
                timeoutMs = etAiTimeout.text?.toString()?.trim() ?: "",
                systemPrompt = etAiSystemPrompt.text?.toString()?.trim() ?: ""
            ),
            asr = RuntimeApiConfig.Asr(
                appId = asrAppId,
                token = asrToken,
                address = asrAddress,
                uri = asrUri,
                resourceId = asrResourceId
            ),
            tts = RuntimeApiConfig.Tts(
                appId = ttsAppId,
                token = ttsToken,
                address = ttsAddress,
                uri = ttsUri,
                resourceId = ttsResourceId
            )
        )
    }

    private fun saveConfig() {
        if (!checkInput()) {
            return
        }
        // 构建配置对象并保存
        val config = buildConfigFromUi()
        viewModel.saveConfig(config)
    }

    companion object {
        const val EXTRA_MANDATORY = "extra_mandatory"
    }

    private fun checkInput(): Boolean {
        // 先检查必填字段是否为空
        val missingFields = mutableListOf<String>()

        val aiBaseUrl = etAiBaseUrl.text?.toString()?.trim() ?: ""
        val aiApiKey = etAiApiKey.text?.toString()?.trim() ?: ""
        val aiModel = etAiModel.text?.toString()?.trim() ?: ""
        val asrAppId = etAsrAppId.text?.toString()?.trim() ?: ""
        val asrToken = etAsrToken.text?.toString()?.trim() ?: ""
        val asrAddress = etAsrAddress.text?.toString()?.trim() ?: ""
        val asrUri = etAsrUri.text?.toString()?.trim() ?: ""
        val asrResourceId = etAsrResourceId.text?.toString()?.trim() ?: ""
        val ttsAppId = etTtsAppId.text?.toString()?.trim() ?: ""
        val ttsToken = etTtsToken.text?.toString()?.trim() ?: ""
        val ttsAddress = etTtsAddress.text?.toString()?.trim() ?: ""
        val ttsUri = etTtsUri.text?.toString()?.trim() ?: ""
        val ttsResourceId = etTtsResourceId.text?.toString()?.trim() ?: ""

        if (aiBaseUrl.isBlank()) missingFields.add("AI Base URL")
        if (aiApiKey.isBlank()) missingFields.add("AI API Key")
        if (aiModel.isBlank()) missingFields.add("AI Model")
        if (asrAppId.isBlank()) missingFields.add("ASR App ID")
        if (asrToken.isBlank()) missingFields.add("ASR Token")
        if (asrAddress.isBlank()) missingFields.add("ASR Address")
        if (asrUri.isBlank()) missingFields.add("ASR URI")
        if (asrResourceId.isBlank()) missingFields.add("ASR Resource ID")
        if (ttsAppId.isBlank()) missingFields.add("TTS App ID")
        if (ttsToken.isBlank()) missingFields.add("TTS Token")
        if (ttsAddress.isBlank()) missingFields.add("TTS Address")
        if (ttsUri.isBlank()) missingFields.add("TTS URI")
        if (ttsResourceId.isBlank()) missingFields.add("TTS Resource ID")

        if (missingFields.isNotEmpty()) {
            Toast.makeText(
                this,
                "请填写必填项: ${missingFields.joinToString(", ")}",
                Toast.LENGTH_LONG
            ).show()
            // 页面不关闭，用户可以继续填写
            return false
        }
        return true
    }
}
