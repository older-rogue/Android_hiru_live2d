# MVVM 架构重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 hiru 项目从单 Activity + Manager 模式重构为标准 MVVM 架构，引入 UseCase 层、Koin 依赖注入、Flow 状态管理。

**Architecture:** 三层架构（UI → Domain → Data），Repository 封装数据访问，UseCase 封装业务逻辑，ViewModel 管理 UI 状态。

**Tech Stack:** Kotlin, Koin, Flow + StateFlow, ViewModel, JUnit 5, MockK

---

## 文件结构

### 新建文件

| 文件路径 | 职责 |
|----------|------|
| `app/src/main/java/com/zx/hiru/HiruApplication.kt` | Application 类，初始化 Koin |
| `app/src/main/java/com/zx/hiru/di/AppModule.kt` | Koin 模块定义 |
| `app/src/main/java/com/zx/hiru/domain/model/ChatState.kt` | 对话状态密封类 |
| `app/src/main/java/com/zx/hiru/domain/model/ChatResponse.kt` | 对话响应领域模型 |
| `app/src/main/java/com/zx/hiru/domain/usecase/ChatUseCase.kt` | 对话流程 UseCase |
| `app/src/main/java/com/zx/hiru/domain/usecase/MemoryUseCase.kt` | 记忆管理 UseCase |
| `app/src/main/java/com/zx/hiru/domain/usecase/ConfigUseCase.kt` | 配置管理 UseCase |
| `app/src/main/java/com/zx/hiru/domain/usecase/MotionUseCase.kt` | Live2D 动作 UseCase |
| `app/src/main/java/com/zx/hiru/data/repository/AiRepository.kt` | AI Repository 接口 |
| `app/src/main/java/com/zx/hiru/data/repository/MemoryRepository.kt` | Memory Repository 接口 |
| `app/src/main/java/com/zx/hiru/data/repository/ConfigRepository.kt` | Config Repository 接口 |
| `app/src/main/java/com/zx/hiru/data/repository/impl/AiRepositoryImpl.kt` | AI Repository 实现 |
| `app/src/main/java/com/zx/hiru/data/repository/impl/MemoryRepositoryImpl.kt` | Memory Repository 实现 |
| `app/src/main/java/com/zx/hiru/data/repository/impl/ConfigRepositoryImpl.kt` | Config Repository 实现 |
| `app/src/main/java/com/zx/hiru/data/service/AsrService.kt` | ASR 服务接口 |
| `app/src/main/java/com/zx/hiru/data/service/TtsService.kt` | TTS 服务接口 |
| `app/src/main/java/com/zx/hiru/data/service/impl/AsrServiceImpl.kt` | ASR 服务实现 |
| `app/src/main/java/com/zx/hiru/data/service/impl/TtsServiceImpl.kt` | TTS 服务实现 |
| `app/src/main/java/com/zx/hiru/ui/main/MainViewModel.kt` | 主页 ViewModel |
| `app/src/main/java/com/zx/hiru/ui/main/MainUiState.kt` | 主页 UI 状态 |
| `app/src/main/java/com/zx/hiru/ui/settings/SettingsViewModel.kt` | 设置页 ViewModel |
| `app/src/main/java/com/zx/hiru/ui/settings/SettingsUiState.kt` | 设置页 UI 状态 |
| `app/src/test/java/com/zx/hiru/domain/usecase/ChatUseCaseTest.kt` | ChatUseCase 单元测试 |
| `app/src/test/java/com/zx/hiru/ui/main/MainViewModelTest.kt` | MainViewModel 单元测试 |

### 修改文件

| 文件路径 | 修改内容 |
|----------|----------|
| `app/build.gradle.kts` | 添加 Koin、ViewModel 依赖 |
| `app/src/main/AndroidManifest.xml` | 添加 Application 类声明 |
| `app/src/main/java/com/zx/hiru/MainActivity.kt` | 使用 ViewModel，移除业务逻辑 |
| `app/src/main/java/com/zx/hiru/config/SettingsActivity.kt` | 使用 ViewModel |

---

## Task 1: 添加依赖

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 添加 Koin 和 ViewModel 依赖**

在 `app/build.gradle.kts` 的 dependencies 块中添加：

```kotlin
// Koin - 依赖注入
implementation("io.insert-koin:koin-android:3.5.3")
implementation("io.insert-koin:koin-androidx-compose:3.5.3")

// ViewModel
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

// Flow
implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

// 测试
testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
testImplementation("io.mockk:mockk:1.13.9")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
testImplementation("app.cash.turbine:turbine:1.0.0")
```

- [ ] **Step 2: 添加 JUnit 5 配置**

在 `app/build.gradle.kts` 中添加：

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
}

// Android 测试配置
android {
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}
```

- [ ] **Step 3: Sync Gradle 并验证**

Run: `./gradlew app:dependencies --configuration implementation | grep -E "(koin|lifecycle)"`

Expected: 显示 koin 和 lifecycle 依赖

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add Koin, ViewModel, and test dependencies"
```

---

## Task 2: 创建领域模型

**Files:**
- Create: `app/src/main/java/com/zx/hiru/domain/model/ChatState.kt`
- Create: `app/src/main/java/com/zx/hiru/domain/model/ChatResponse.kt`

- [ ] **Step 1: 创建 ChatState 密封类**

```kotlin
// app/src/main/java/com/zx/hiru/domain/model/ChatState.kt
package com.zx.hiru.domain.model

/**
 * 对话状态
 */
sealed class ChatState {
    /** 空闲状态 */
    object Idle : ChatState()

    /** 正在验证输入 */
    object Validating : ChatState()

    /** 内容被过滤 */
    data class Filtered(val text: String) : ChatState()

    /** AI 正在思考 */
    object Thinking : ChatState()

    /** AI 正在回复 */
    data class Responding(val response: ChatResponse) : ChatState()

    /** 发生错误 */
    data class Error(val message: String) : ChatState()
}
```

- [ ] **Step 2: 创建 ChatResponse 领域模型**

```kotlin
// app/src/main/java/com/zx/hiru/domain/model/ChatResponse.kt
package com.zx.hiru.domain.model

/**
 * 对话响应领域模型
 *
 * 注意：这是领域层的封装，底层使用 AiResponse.SystemBackContent
 */
data class ChatResponse(
    val text: String,
    val tone: String?,
    val action: String?
)

/**
 * 将 AiResponse.SystemBackContent 转换为 ChatResponse
 */
fun com.zx.hiru.ai.AiResponse.SystemBackContent.toChatResponse(): ChatResponse {
    return ChatResponse(
        text = text ?: "",
        tone = tone,
        action = action
    )
}
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zx/hiru/domain/
git commit -m "feat(domain): add ChatState and ChatResponse domain models"
```

---

## Task 3: 创建 Repository 接口

**Files:**
- Create: `app/src/main/java/com/zx/hiru/data/repository/AiRepository.kt`
- Create: `app/src/main/java/com/zx/hiru/data/repository/MemoryRepository.kt`
- Create: `app/src/main/java/com/zx/hiru/data/repository/ConfigRepository.kt`

- [ ] **Step 1: 创建 AiRepository 接口**

```kotlin
// app/src/main/java/com/zx/hiru/data/repository/AiRepository.kt
package com.zx.hiru.data.repository

import com.zx.hiru.domain.model.ChatResponse

/**
 * AI 对话仓库接口
 */
interface AiRepository {
    /**
     * 发送消息并获取 AI 回复
     * @param input 用户输入
     * @param memoryContext 记忆上下文
     * @return 对话响应结果
     */
    suspend fun chat(input: String, memoryContext: String?): Result<ChatResponse>

    /**
     * 清空对话历史
     */
    fun clearHistory()
}
```

- [ ] **Step 2: 创建 MemoryRepository 接口**

```kotlin
// app/src/main/java/com/zx/hiru/data/repository/MemoryRepository.kt
package com.zx.hiru.data.repository

/**
 * 记忆仓库接口
 */
interface MemoryRepository {
    /**
     * 获取记忆原文
     */
    fun getMemory(): String

    /**
     * 获取记忆上下文（用于注入 AI 提示词）
     */
    fun getMemoryContext(): String

    /**
     * 更新记忆
     * @param userInput 用户输入
     * @param aiResponse AI 回复
     */
    suspend fun updateMemory(userInput: String, aiResponse: String): Result<Unit>

    /**
     * 清空记忆
     */
    fun clearMemory()
}
```

- [ ] **Step 3: 创建 ConfigRepository 接口**

```kotlin
// app/src/main/java/com/zx/hiru/data/repository/ConfigRepository.kt
package com.zx.hiru.data.repository

import com.zx.hiru.config.RuntimeApiConfig

/**
 * 配置仓库接口
 */
interface ConfigRepository {
    /**
     * 加载配置
     */
    fun loadConfig(): RuntimeApiConfig

    /**
     * 保存配置
     */
    fun saveConfig(config: RuntimeApiConfig)
}
```

- [ ] **Step 4: 验证编译**

Run: `./gradlew app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zx/hiru/data/repository/
git commit -m "feat(data): add Repository interfaces"
```

---

## Task 4: 创建 Service 接口

**Files:**
- Create: `app/src/main/java/com/zx/hiru/data/service/AsrService.kt`
- Create: `app/src/main/java/com/zx/hiru/data/service/TtsService.kt`

- [ ] **Step 1: 创建 AsrService 接口**

```kotlin
// app/src/main/java/com/zx/hiru/data/service/AsrService.kt
package com.zx.hiru.data.service

/**
 * 语音识别服务接口
 */
interface AsrService {
    /**
     * 开始语音识别
     */
    fun startRecognition(callback: AsrCallback)

    /**
     * 停止语音识别
     */
    fun stopRecognition()

    /**
     * 释放资源
     */
    fun release()

    /**
     * 语音识别回调
     */
    interface AsrCallback {
        fun onResult(text: String, isFinal: Boolean)
        fun onError(code: Int, message: String)
    }
}
```

- [ ] **Step 2: 创建 TtsService 接口**

```kotlin
// app/src/main/java/com/zx/hiru/data/service/TtsService.kt
package com.zx.hiru.data.service

/**
 * 语音合成服务接口
 */
interface TtsService {
    /**
     * 开始语音合成
     */
    fun speak(text: String, emotion: String?, callback: TtsCallback)

    /**
     * 停止播放
     */
    fun stop()

    /**
     * 释放资源
     */
    fun release()

    /**
     * 语音合成回调
     */
    interface TtsCallback {
        fun onPlayStart()
        fun onPlayComplete()
        fun onError(code: Int, message: String)
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zx/hiru/data/service/
git commit -m "feat(data): add AsrService and TtsService interfaces"
```

---

## Task 5: 实现 Repository

**Files:**
- Create: `app/src/main/java/com/zx/hiru/data/repository/impl/AiRepositoryImpl.kt`
- Create: `app/src/main/java/com/zx/hiru/data/repository/impl/MemoryRepositoryImpl.kt`
- Create: `app/src/main/java/com/zx/hiru/data/repository/impl/ConfigRepositoryImpl.kt`

- [ ] **Step 1: 实现 AiRepository**

```kotlin
// app/src/main/java/com/zx/hiru/data/repository/impl/AiRepositoryImpl.kt
package com.zx.hiru.data.repository.impl

import com.zx.hiru.ai.AiClient
import com.zx.hiru.ai.AiConfig
import com.zx.hiru.config.AppConfig
import com.zx.hiru.data.repository.AiRepository
import com.zx.hiru.domain.model.ChatResponse
import com.zx.hiru.domain.model.toChatResponse

/**
 * AI Repository 实现
 *
 * 注意：需要在首次使用前调用 init() 初始化 AiClient
 */
class AiRepositoryImpl : AiRepository {

    private var aiClient: AiClient? = null

    /**
     * 初始化 AI 客户端
     * @param config AI 配置，如果为 null 则从 AppConfig 加载
     */
    fun init(config: AiConfig? = null) {
        aiClient?.close()
        val aiConfig = config ?: AiConfig.fromRuntimeConfig(AppConfig.getConfig().ai)
        aiClient = AiClient(aiConfig)
    }

    override suspend fun chat(input: String, memoryContext: String?): Result<ChatResponse> {
        // 如果未初始化，自动使用默认配置初始化
        if (aiClient == null) {
            init()
        }
        val client = aiClient ?: return Result.failure(IllegalStateException("AiClient not initialized"))
        return runCatching {
            val response = client.chat(input, memoryContext)
            response.toChatResponse()
        }
    }

    override fun clearHistory() {
        // AiClient 内部管理历史，这里需要重新创建客户端
        init()
    }

    fun close() {
        aiClient?.close()
        aiClient = null
    }
}
```

- [ ] **Step 2: 实现 MemoryRepository**

```kotlin
// app/src/main/java/com/zx/hiru/data/repository/impl/MemoryRepositoryImpl.kt
package com.zx.hiru.data.repository.impl

import android.content.Context
import com.zx.hiru.ai.MemoryManager
import com.zx.hiru.data.repository.MemoryRepository

/**
 * Memory Repository 实现
 *
 * 封装 MemoryManager 单例
 */
class MemoryRepositoryImpl(
    context: Context
) : MemoryRepository {

    private val appContext = context.applicationContext
    private val memoryManager = MemoryManager.getInstance()

    // 初始化块：在构造时自动初始化 MemoryManager
    init {
        memoryManager.init(appContext)
    }

    override fun getMemory(): String = memoryManager.getMemory()

    override fun getMemoryContext(): String = memoryManager.getMemoryContext()

    override suspend fun updateMemory(userInput: String, aiResponse: String): Result<Unit> {
        return runCatching {
            memoryManager.processConversation(userInput, aiResponse)
        }
    }

    override fun clearMemory() {
        memoryManager.clearMemory()
    }
}
```

- [ ] **Step 3: 实现 ConfigRepository**

```kotlin
// app/src/main/java/com/zx/hiru/data/repository/impl/ConfigRepositoryImpl.kt
package com.zx.hiru.data.repository.impl

import com.zx.hiru.config.RuntimeApiConfig
import com.zx.hiru.config.RuntimeConfigRepository
import com.zx.hiru.data.repository.ConfigRepository

/**
 * Config Repository 实现
 *
 * 适配现有 RuntimeConfigRepository
 */
class ConfigRepositoryImpl(
    private val runtimeConfigRepository: RuntimeConfigRepository
) : ConfigRepository {

    override fun loadConfig(): RuntimeApiConfig {
        return runtimeConfigRepository.load()
    }

    override fun saveConfig(config: RuntimeApiConfig) {
        runtimeConfigRepository.save(config)
    }
}
```

- [ ] **Step 4: 验证编译**

Run: `./gradlew app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zx/hiru/data/repository/impl/
git commit -m "feat(data): implement Repository classes"
```

---

## Task 6: 实现 Service

**Files:**
- Create: `app/src/main/java/com/zx/hiru/data/service/impl/AsrServiceImpl.kt`
- Create: `app/src/main/java/com/zx/hiru/data/service/impl/TtsServiceImpl.kt`

- [ ] **Step 1: 实现 AsrService**

```kotlin
// app/src/main/java/com/zx/hiru/data/service/impl/AsrServiceImpl.kt
package com.zx.hiru.data.service.impl

import android.content.Context
import com.zx.hiru.data.service.AsrService
import com.zx.hiru.tts.AsrManager

/**
 * ASR Service 实现
 *
 * 封装 AsrManager 单例
 * 在构造时自动设置 Context
 */
class AsrServiceImpl(
    context: Context
) : AsrService {

    private val appContext = context.applicationContext
    private val asrManager = AsrManager.getInstance()

    init {
        asrManager.setContext(appContext)
    }

    override fun startRecognition(callback: AsrService.AsrCallback) {
        asrManager.startRecognition(object : AsrManager.AsrCallback {
            override fun onResult(text: String, isFinal: Boolean) {
                callback.onResult(text, isFinal)
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                callback.onError(errorCode, errorMessage)
            }
        })
    }

    override fun stopRecognition() {
        asrManager.stopRecognition()
    }

    override fun release() {
        asrManager.release()
    }
}
```

- [ ] **Step 2: 实现 TtsService**

```kotlin
// app/src/main/java/com/zx/hiru/data/service/impl/TtsServiceImpl.kt
package com.zx.hiru.data.service.impl

import android.content.Context
import com.zx.hiru.data.service.TtsService
import com.zx.hiru.tts.TtsManager

/**
 * TTS Service 实现
 *
 * 封装 TtsManager 单例
 * 在构造时自动设置 Context
 */
class TtsServiceImpl(
    context: Context
) : TtsService {

    private val appContext = context.applicationContext
    private val ttsManager = TtsManager.getInstance()

    init {
        ttsManager.setContext(appContext)
    }

    override fun speak(text: String, emotion: String?, callback: TtsService.TtsCallback) {
        ttsManager.speak(text, emotion, object : TtsManager.TtsCallback {
            override fun onSynthesisStart(text: String) {
                // 合成开始，暂不处理
            }

            override fun onSynthesisComplete() {
                // 合成完成，暂不处理
            }

            override fun onPlayStart() {
                callback.onPlayStart()
            }

            override fun onPlayComplete() {
                callback.onPlayComplete()
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                callback.onError(errorCode, errorMessage)
            }
        })
    }

    override fun stop() {
        ttsManager.stop()
    }

    override fun release() {
        ttsManager.release()
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zx/hiru/data/service/impl/
git commit -m "feat(data): implement AsrService and TtsService"
```

---

## Task 7: 创建 UseCase

**Files:**
- Create: `app/src/main/java/com/zx/hiru/domain/usecase/ChatUseCase.kt`
- Create: `app/src/main/java/com/zx/hiru/domain/usecase/MemoryUseCase.kt`
- Create: `app/src/main/java/com/zx/hiru/domain/usecase/ConfigUseCase.kt`
- Create: `app/src/main/java/com/zx/hiru/domain/usecase/MotionUseCase.kt`

- [ ] **Step 1: 创建 ChatUseCase**

```kotlin
// app/src/main/java/com/zx/hiru/domain/usecase/ChatUseCase.kt
package com.zx.hiru.domain.usecase

import com.zx.hiru.ai.DialogValidator
import com.zx.hiru.data.repository.AiRepository
import com.zx.hiru.data.repository.MemoryRepository
import com.zx.hiru.domain.model.ChatResponse
import com.zx.hiru.domain.model.ChatState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 对话 UseCase
 *
 * 封装完整对话流程：验证 → 思考 → 回复
 */
class ChatUseCase(
    private val aiRepository: AiRepository,
    private val memoryRepository: MemoryRepository,
    private val dialogValidator: DialogValidator
) {
    /**
     * 执行对话流程
     * @param input 用户输入
     * @return 对话状态流
     */
    fun execute(input: String): Flow<ChatState> = flow {
        // 1. 验证输入有效性（DialogValidator.validate 是 suspend 函数）
        emit(ChatState.Validating)
        val isValid = dialogValidator.validate(input)
        if (!isValid) {
            emit(ChatState.Filtered(input))
            return@flow
        }

        // 2. 获取记忆上下文
        val memoryContext = memoryRepository.getMemoryContext()

        // 3. 调用 AI
        emit(ChatState.Thinking)
        val result = aiRepository.chat(input, memoryContext)

        // 4. 返回结果
        when {
            result.isSuccess -> {
                val response = result.getOrThrow()
                emit(ChatState.Responding(response))
                // 5. 异步更新记忆（不阻塞响应）
                memoryRepository.updateMemory(input, response.text)
            }
            result.isFailure -> {
                emit(ChatState.Error(result.exceptionOrNull()?.message ?: "Unknown error"))
            }
        }
    }

    /**
     * 清空对话历史
     */
    fun clearHistory() = aiRepository.clearHistory()
}
```

- [ ] **Step 2: 创建 MemoryUseCase**

```kotlin
// app/src/main/java/com/zx/hiru/domain/usecase/MemoryUseCase.kt
package com.zx.hiru.domain.usecase

import com.zx.hiru.data.repository.MemoryRepository

/**
 * 记忆 UseCase
 *
 * 管理记忆的读取、更新、持久化
 */
class MemoryUseCase(
    private val memoryRepository: MemoryRepository
) {
    /**
     * 对话后更新记忆
     */
    suspend fun updateAfterConversation(userInput: String, aiResponse: String): Result<Unit> {
        return memoryRepository.updateMemory(userInput, aiResponse)
    }

    /**
     * 获取记忆上下文（用于注入 AI 提示词）
     */
    fun getMemoryContext(): String = memoryRepository.getMemoryContext()

    /**
     * 获取记忆原文
     */
    fun getMemory(): String = memoryRepository.getMemory()

    /**
     * 清空记忆
     */
    fun clearMemory() = memoryRepository.clearMemory()
}
```

- [ ] **Step 3: 创建 ConfigUseCase**

```kotlin
// app/src/main/java/com/zx/hiru/domain/usecase/ConfigUseCase.kt
package com.zx.hiru.domain.usecase

import com.zx.hiru.config.RuntimeApiConfig
import com.zx.hiru.data.repository.ConfigRepository

/**
 * 配置 UseCase
 *
 * 管理运行时配置
 */
class ConfigUseCase(
    private val configRepository: ConfigRepository
) {
    /**
     * 加载配置
     */
    fun loadConfig(): RuntimeApiConfig = configRepository.loadConfig()

    /**
     * 保存配置
     */
    fun saveConfig(config: RuntimeApiConfig) = configRepository.saveConfig(config)

    /**
     * 检查配置是否完整
     */
    fun isConfigComplete(): Boolean = configRepository.loadConfig().isComplete()
}
```

- [ ] **Step 4: 创建 MotionUseCase**

```kotlin
// app/src/main/java/com/zx/hiru/domain/usecase/MotionUseCase.kt
package com.zx.hiru.domain.usecase

import android.os.Handler
import android.os.Looper
import com.zx.live2d.LAppDefine
import com.zx.live2d.LAppLive2DManager
import com.zx.live2d.LAppModel

/**
 * 动作 UseCase
 *
 * 管理 Live2D 动作触发
 *
 * 注意：此 UseCase 依赖 live2d 模块的 LAppLive2DManager，属于基础设施依赖
 */
class MotionUseCase(
    private val live2DManager: LAppLive2DManager
) {
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
```

- [ ] **Step 5: 验证编译**

Run: `./gradlew app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zx/hiru/domain/usecase/
git commit -m "feat(domain): add UseCase classes"
```

---

## Task 8: 创建 UI 状态类

**Files:**
- Create: `app/src/main/java/com/zx/hiru/ui/main/MainUiState.kt`
- Create: `app/src/main/java/com/zx/hiru/ui/settings/SettingsUiState.kt`

- [ ] **Step 1: 创建 MainUiState**

```kotlin
// app/src/main/java/com/zx/hiru/ui/main/MainUiState.kt
package com.zx.hiru.ui.main

/**
 * 主页 UI 状态
 */
data class MainUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isProcessing: Boolean = false,
    val needsConfig: Boolean = false,
    val error: String? = null
) {
    val isIdle: Boolean
        get() = !isListening && !isSpeaking && !isProcessing
}
```

- [ ] **Step 2: 创建 SettingsUiState**

```kotlin
// app/src/main/java/com/zx/hiru/ui/settings/SettingsUiState.kt
package com.zx.hiru.ui.settings

import com.zx.hiru.config.RuntimeApiConfig

/**
 * 设置页 UI 状态
 */
data class SettingsUiState(
    val config: RuntimeApiConfig? = null,
    val saved: Boolean = false,
    val error: String? = null
)
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zx/hiru/ui/
git commit -m "feat(ui): add UiState classes"
```

---

## Task 9: 创建 ViewModel

**Files:**
- Create: `app/src/main/java/com/zx/hiru/ui/main/MainViewModel.kt`
- Create: `app/src/main/java/com/zx/hiru/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: 创建 MainViewModel**

```kotlin
// app/src/main/java/com/zx/hiru/ui/main/MainViewModel.kt
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
```

- [ ] **Step 2: 创建 SettingsViewModel**

```kotlin
// app/src/main/java/com/zx/hiru/ui/settings/SettingsViewModel.kt
package com.zx.hiru.ui.settings

import androidx.lifecycle.ViewModel
import com.zx.hiru.config.RuntimeApiConfig
import com.zx.hiru.domain.usecase.ConfigUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置页 ViewModel
 */
class SettingsViewModel(
    private val configUseCase: ConfigUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    /**
     * 加载配置
     */
    fun loadConfig() {
        val config = configUseCase.loadConfig()
        _uiState.value = _uiState.value.copy(config = config)
    }

    /**
     * 保存配置
     */
    fun saveConfig(config: RuntimeApiConfig) {
        configUseCase.saveConfig(config)
        _uiState.value = _uiState.value.copy(config = config, saved = true)
    }

    /**
     * 重置保存状态
     */
    fun resetSaved() {
        _uiState.value = _uiState.value.copy(saved = false)
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zx/hiru/ui/main/MainViewModel.kt app/src/main/java/com/zx/hiru/ui/settings/SettingsViewModel.kt
git commit -m "feat(ui): add MainViewModel and SettingsViewModel"
```

---

## Task 10: 配置 Koin

**Files:**
- Create: `app/src/main/java/com/zx/hiru/di/AppModule.kt`
- Create: `app/src/main/java/com/zx/hiru/HiruApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 创建 Koin 模块**

```kotlin
// app/src/main/java/com/zx/hiru/di/AppModule.kt
package com.zx.hiru.di

import com.zx.hiru.ai.DialogValidator
import com.zx.hiru.cache.CacheManager
import com.zx.hiru.config.RuntimeConfigRepository
import com.zx.hiru.data.repository.AiRepository
import com.zx.hiru.data.repository.ConfigRepository
import com.zx.hiru.data.repository.MemoryRepository
import com.zx.hiru.data.repository.impl.AiRepositoryImpl
import com.zx.hiru.data.repository.impl.ConfigRepositoryImpl
import com.zx.hiru.data.repository.impl.MemoryRepositoryImpl
import com.zx.hiru.data.service.AsrService
import com.zx.hiru.data.service.TtsService
import com.zx.hiru.data.service.impl.AsrServiceImpl
import com.zx.hiru.data.service.impl.TtsServiceImpl
import com.zx.hiru.domain.usecase.ChatUseCase
import com.zx.hiru.domain.usecase.ConfigUseCase
import com.zx.hiru.domain.usecase.MemoryUseCase
import com.zx.hiru.domain.usecase.MotionUseCase
import com.zx.hiru.ui.main.MainViewModel
import com.zx.hiru.ui.settings.SettingsViewModel
import com.zx.live2d.LAppLive2DManager
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin 应用模块
 */
val appModule = module {
    // ViewModel
    viewModel { MainViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }

    // UseCase
    factory { ChatUseCase(get(), get(), get()) }
    factory { MemoryUseCase(get()) }
    factory { ConfigUseCase(get()) }
    factory { MotionUseCase(get()) }

    // Repository
    single<AiRepository> { AiRepositoryImpl() }
    single<MemoryRepository> { MemoryRepositoryImpl(androidContext()) }
    single<ConfigRepository> { ConfigRepositoryImpl(get()) }

    // Service - 需要传入 Context
    single<AsrService> { AsrServiceImpl(androidContext()) }
    single<TtsService> { TtsServiceImpl(androidContext()) }

    // Validator
    single { DialogValidator() }

    // Live2D Manager
    single { LAppLive2DManager.getInstance() }

    // Runtime Config Repository
    single { RuntimeConfigRepository() }
}
```

- [ ] **Step 2: 创建 Application 类**

```kotlin
// app/src/main/java/com/zx/hiru/HiruApplication.kt
package com.zx.hiru

import android.app.Application
import com.zx.hiru.cache.CacheManager
import com.zx.hiru.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * 应用程序入口
 */
class HiruApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 初始化缓存
        CacheManager.init(this)

        // 初始化 Koin
        startKoin {
            androidContext(this@HiruApplication)
            modules(appModule)
        }
    }
}
```

- [ ] **Step 3: 更新 AndroidManifest.xml**

在 `<application>` 标签中添加 `android:name` 属性：

```xml
<application
    android:name=".HiruApplication"
    ... >
```

- [ ] **Step 4: 验证编译**

Run: `./gradlew app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zx/hiru/di/ app/src/main/java/com/zx/hiru/HiruApplication.kt app/src/main/AndroidManifest.xml
git commit -m "feat(di): add Koin configuration and Application class"
```

---

## Task 11: 重构 MainActivity

**Files:**
- Modify: `app/src/main/java/com/zx/hiru/MainActivity.kt`

- [ ] **Step 1: 添加 ViewModel 导入和属性**

在 MainActivity.kt 顶部添加导入：

```kotlin
import androidx.lifecycle.lifecycleScope
import com.zx.hiru.ui.main.MainViewModel
import com.zx.hiru.domain.model.ChatState
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
```

在类中添加 ViewModel 属性：

```kotlin
private val viewModel: MainViewModel by viewModel()
```

- [ ] **Step 2: 移除 VoiceChatManager 和相关属性**

删除以下代码：
- `private val voiceChatManager = VoiceChatManager()`
- `private lateinit var configRepository: RuntimeConfigRepository`
- `private var hasAudioPermission = false`
- 所有 VoiceChatManager 相关的方法和回调

- [ ] **Step 3: 添加 UI 状态观察**

在 `onCreate()` 中添加状态观察：

```kotlin
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
```

- [ ] **Step 4: 简化 initConfigRepository 方法**

删除 `initConfigRepository()` 方法，配置检查已移至 ViewModel。

- [ ] **Step 5: 简化权限处理**

修改 `onRequestPermissionsResult`：

```kotlin
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
```

- [ ] **Step 6: 简化 onSettingsSaved 方法**

```kotlin
private fun onSettingsSaved() {
    viewModel.onConfigUpdated()
}
```

- [ ] **Step 7: 移除 VoiceChatManager 初始化和回调**

删除 `initVoiceChatManager()` 方法和所有回调代码。

- [ ] **Step 8: 验证编译**

Run: `./gradlew app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/zx/hiru/MainActivity.kt
git commit -m "refactor(ui): migrate MainActivity to MVVM"
```

---

## Task 12: 重构 SettingsActivity

**Files:**
- Modify: `app/src/main/java/com/zx/hiru/config/SettingsActivity.kt`

- [ ] **Step 1: 添加 ViewModel 导入和属性**

在 SettingsActivity.kt 顶部添加导入：

```kotlin
import androidx.lifecycle.lifecycleScope
import com.zx.hiru.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
```

在类中添加 ViewModel 属性：

```kotlin
private val viewModel: SettingsViewModel by viewModel()
```

- [ ] **Step 2: 移除直接使用的 RuntimeConfigRepository**

删除：
- `private lateinit var configRepository: RuntimeConfigRepository`
- `configRepository = RuntimeConfigRepository()` 初始化代码

- [ ] **Step 3: 添加 UI 状态观察**

在 `onCreate()` 中添加状态观察：

```kotlin
lifecycleScope.launch {
    viewModel.uiState.collect { state ->
        state.config?.let { config ->
            // 更新 UI 显示配置
            updateUiFromConfig(config)
        }
        if (state.saved) {
            setResult(RESULT_OK)
            viewModel.resetSaved()
        }
    }
}
```

- [ ] **Step 4: 修改保存逻辑使用 ViewModel**

```kotlin
private fun saveConfig() {
    val config = buildConfigFromUi()
    viewModel.saveConfig(config)
}
```

- [ ] **Step 5: 验证编译**

Run: `./gradlew app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zx/hiru/config/SettingsActivity.kt
git commit -m "refactor(ui): migrate SettingsActivity to MVVM"
```

---

## Task 13: 编写单元测试

**Files:**
- Create: `app/src/test/java/com/zx/hiru/domain/usecase/ChatUseCaseTest.kt`
- Create: `app/src/test/java/com/zx/hiru/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: 编写 ChatUseCase 测试**

```kotlin
// app/src/test/java/com/zx/hiru/domain/usecase/ChatUseCaseTest.kt
package com.zx.hiru.domain.usecase

import com.zx.hiru.ai.DialogValidator
import com.zx.hiru.data.repository.AiRepository
import com.zx.hiru.data.repository.MemoryRepository
import com.zx.hiru.domain.model.ChatResponse
import com.zx.hiru.domain.model.ChatState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatUseCaseTest {

    private lateinit var aiRepository: AiRepository
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var dialogValidator: DialogValidator
    private lateinit var chatUseCase: ChatUseCase

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        aiRepository = mockk()
        memoryRepository = mockk()
        dialogValidator = mockk()
        chatUseCase = ChatUseCase(aiRepository, memoryRepository, dialogValidator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when input is invalid, should emit Filtered state`() = runTest {
        // Given - DialogValidator.validate 是 suspend 函数，使用 coEvery
        coEvery { dialogValidator.validate("test") } returns false

        // When
        val flow = chatUseCase.execute("test")
        val states = mutableListOf<ChatState>()
        flow.collect { states.add(it) }

        // Then
        assertTrue(states.any { it is ChatState.Filtered })
    }

    @Test
    fun `when input is valid, should emit Thinking and Responding states`() = runTest {
        // Given
        val response = ChatResponse("Hello", null, null)
        coEvery { dialogValidator.validate("hello") } returns true
        every { memoryRepository.getMemoryContext() } returns ""
        coEvery { aiRepository.chat("hello", null) } returns Result.success(response)
        coEvery { memoryRepository.updateMemory(any(), any()) } returns Result.success(Unit)

        // When
        val flow = chatUseCase.execute("hello")
        val states = mutableListOf<ChatState>()
        flow.collect { states.add(it) }

        // Then
        assertTrue(states.any { it is ChatState.Thinking })
        assertTrue(states.any { it is ChatState.Responding })
    }
}
```

- [ ] **Step 2: 运行测试验证**

Run: `./gradlew app:testDebugUnitTest --tests "com.zx.hiru.domain.usecase.ChatUseCaseTest"`

Expected: Tests pass

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/zx/hiru/domain/usecase/
git commit -m "test: add ChatUseCase unit tests"
```

---

## Task 14: 集成测试

**Files:**
- None

- [ ] **Step 1: 编译整个项目**

Run: `./gradlew app:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 手动测试核心功能**

测试清单：
1. [ ] 应用启动后显示 Live2D 模型
2. [ ] 配置缺失时跳转设置页
3. [ ] 语音识别正常工作
4. [ ] AI 对话正常返回
5. [ ] 语音合成正常播放
6. [ ] Live2D 动作正常触发

- [ ] **Step 3: 最终 Commit**

```bash
git add -A
git commit -m "feat: complete MVVM architecture refactoring"
```

---

## 回滚计划

如果重构过程中出现问题，可以使用以下命令回滚：

```bash
# 查看提交历史
git log --oneline

# 回滚到指定提交
git reset --hard <commit-hash>

# 或者创建新分支保留当前工作
git checkout -b mvvm-backup
git checkout main
git reset --hard <last-stable-commit>
```
