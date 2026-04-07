package com.zx.hiru.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zx.hiru.data.service.AsrService
import com.zx.hiru.data.service.TtsService
import com.zx.hiru.domain.model.ChatState
import com.zx.hiru.domain.usecase.ChatUseCase
import com.zx.hiru.domain.usecase.ConfigUseCase
import com.zx.hiru.domain.usecase.MemoryUseCase
import com.zx.hiru.domain.usecase.MotionUseCase
import com.zx.hiru.tts.EmotionMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 主页 ViewModel
 *
 * 管理 UI 状态、处理用户交互、协调 UseCase
 */
class MainViewModel(
    private val chatUseCase: ChatUseCase,
    private val memoryUseCase: MemoryUseCase,
    private val configUseCase: ConfigUseCase,
    private val motionUseCase: MotionUseCase,
    private val asrService: AsrService,
    private val ttsService: TtsService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _chatState = MutableStateFlow<ChatState>(ChatState.Idle)
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    // 当前对话（用于记忆更新）
    private var currentUserInput: String = ""
    private var currentAiResponse: String = ""

    init {
        checkConfigAndStart()
    }

    /**
     * 检查配置并启动监听
     */
    private fun checkConfigAndStart() {
        val isComplete = configUseCase.isConfigComplete()
        _uiState.update { it.copy(needsConfig = !isComplete) }
    }

    /**
     * 开始语音识别
     */
    fun startListening() {
        if (_uiState.value.isProcessing || _uiState.value.needsConfig) return

        _uiState.update { it.copy(isListening = true, error = null) }
        asrService.startRecognition(object : AsrService.AsrCallback {
            override fun onResult(text: String, isFinal: Boolean) {
                if (isFinal && text.isNotEmpty()) {
                    _uiState.update { it.copy(isListening = false) }
                    processUserInput(text)
                }
            }

            override fun onError(code: Int, message: String) {
                _uiState.update { it.copy(isListening = false, error = "ASR错误: $message") }
                // 自动重试
                viewModelScope.launch {
                    delay(300)
                    startListening()
                }
            }
        })
    }

    /**
     * 处理用户输入
     */
    private fun processUserInput(text: String) {
        currentUserInput = text
        _uiState.update { it.copy(isProcessing = true) }

        viewModelScope.launch {
            chatUseCase.execute(text).collect { state ->
                _chatState.value = state

                when (state) {
                    is ChatState.Thinking -> motionUseCase.startThinkingMotion()
                    is ChatState.Responding -> {
                        motionUseCase.stopThinkingMotion()
                        currentAiResponse = state.response.text
                        state.response.action?.let { motionUseCase.playEmotionMotion(it) }
                        speak(state.response.text, state.response.tone)
                    }
                    is ChatState.Filtered -> {
                        _uiState.update { it.copy(isProcessing = false) }
                        startListening()
                    }
                    is ChatState.Error -> {
                        _uiState.update { it.copy(isProcessing = false, error = state.message) }
                        startListening()
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * 语音合成
     */
    private fun speak(text: String, tone: String?) {
        val emotion = EmotionMapper.mapToTtsEmotion(tone)
        ttsService.speak(text, emotion, object : TtsService.TtsCallback {
            override fun onPlayStart() {
                _uiState.update { it.copy(isSpeaking = true) }
            }

            override fun onPlayComplete() {
                _uiState.update { it.copy(isSpeaking = false, isProcessing = false) }
                // 更新记忆并重新监听
                viewModelScope.launch {
                    if (currentUserInput.isNotEmpty() && currentAiResponse.isNotEmpty()) {
                        memoryUseCase.updateAfterConversation(currentUserInput, currentAiResponse)
                    }
                    startListening()
                }
            }

            override fun onError(code: Int, message: String) {
                _uiState.update { it.copy(isSpeaking = false, isProcessing = false, error = "TTS错误: $message") }
                startListening()
            }
        })
    }

    /**
     * 配置已更新
     */
    fun onConfigUpdated() {
        _uiState.update { it.copy(needsConfig = false) }
        startListening()
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
