# MVVM 架构重构设计文档

## 概述

将 hiru 项目从当前的单 Activity + Manager 模式重构为标准 MVVM 架构，引入 UseCase 层封装业务逻辑，使用 Koin 进行依赖注入，Flow + StateFlow 管理 UI 状态。

## 技术选型

| 技术 | 选择 | 理由 |
|------|------|------|
| 依赖注入 | Koin | 轻量级，易于上手 |
| 状态管理 | Flow + StateFlow | 现代 Android 开发实践 |
| 架构模式 | MVVM + UseCase | 清晰分层，易于测试 |

## 架构设计

### 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer                           │
│  ┌─────────────┐    ┌─────────────────────────────┐    │
│  │ MainActivity │───▶│ MainViewModel               │    │
│  │ SettingsAct. │───▶│ SettingsViewModel           │    │
│  └─────────────┘    └─────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
                          │
┌─────────────────────────────────────────────────────────┐
│                    Domain Layer                         │
│  ┌─────────────────┐  ┌─────────────────┐              │
│  │ ChatUseCase      │  │ MemoryUseCase   │              │
│  │ ConfigUseCase    │  │ MotionUseCase   │              │
│  └─────────────────┘  └─────────────────┘              │
└─────────────────────────────────────────────────────────┘
                          │
┌─────────────────────────────────────────────────────────┐
│                     Data Layer                          │
│  ┌───────────────┐  ┌───────────────┐  ┌────────────┐ │
│  │ AiRepository   │  │ ConfigRepo    │  │ MemoryRepo │ │
│  └───────────────┘  └───────────────┘  └────────────┘ │
│  ┌───────────────┐  ┌───────────────┐                  │
│  │ AsrService    │  │ TtsService    │                  │
│  └───────────────┘  └───────────────┘                  │
└─────────────────────────────────────────────────────────┘
```

### 包结构

```
com.zx.hiru/
├── HiruApplication.kt          # Application 类，初始化 Koin
├── di/
│   ├── AppModule.kt            # Koin 模块定义
│   └── NetworkModule.kt
├── ui/
│   ├── main/
│   │   ├── MainActivity.kt
│   │   ├── MainViewModel.kt
│   │   └── MainUiState.kt
│   └── settings/
│       ├── SettingsActivity.kt
│       ├── SettingsViewModel.kt
│       └── SettingsUiState.kt
├── domain/
│   ├── usecase/
│   │   ├── ChatUseCase.kt
│   │   ├── MemoryUseCase.kt
│   │   ├── ConfigUseCase.kt
│   │   └── MotionUseCase.kt
│   └── model/
│       ├── ChatMessage.kt
│       ├── ChatSession.kt
│       └── ChatState.kt
├── data/
│   ├── repository/
│   │   ├── AiRepository.kt
│   │   ├── MemoryRepository.kt
│   │   └── ConfigRepository.kt
│   └── service/
│       ├── AsrService.kt
│       ├── TtsService.kt
│       └── AiService.kt
└── util/
    └── NetworkKit.kt
```

## 数据层设计

### Repository 接口

```kotlin
// AiRepository.kt
interface AiRepository {
    suspend fun chat(input: String, memoryContext: String?): Result<ChatResponse>
    fun clearHistory()
}

// MemoryRepository.kt
interface MemoryRepository {
    fun getMemory(): String
    fun getMemoryContext(): String
    suspend fun updateMemory(userInput: String, aiResponse: String): Result<Unit>
    fun clearMemory()
}

// ConfigRepository.kt
// 注意：方法名使用 loadConfig/saveConfig，与现有 RuntimeConfigRepository 的 load/save 不同
// 实现时需要适配或重命名
interface ConfigRepository {
    fun loadConfig(): RuntimeApiConfig
    fun saveConfig(config: RuntimeApiConfig)
}
```

### Service 接口

```kotlin
// AsrService.kt
interface AsrService {
    fun startRecognition(callback: AsrCallback)
    fun stopRecognition()
    fun release()

    interface AsrCallback {
        fun onResult(text: String, isFinal: Boolean)
        fun onError(code: Int, message: String)
    }
}

// TtsService.kt
interface TtsService {
    fun speak(text: String, emotion: String?, callback: TtsCallback)
    fun stop()
    fun release()

    interface TtsCallback {
        fun onPlayStart()
        fun onPlayComplete()
        fun onError(code: Int, message: String)
    }
}

// AiService.kt
// 封装 AI API 调用，底层使用现有的 AiClient
interface AiService {
    suspend fun chat(input: String, memoryContext: String?): Result<AiResponse.SystemBackContent>
    fun clearHistory()
}
```

### 领域模型

```kotlin
// ChatMessage.kt
data class ChatMessage(
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role { USER, ASSISTANT }
}

// ChatResponse.kt
// 注意：这是领域层的封装，底层使用 AiResponse.SystemBackContent
// 实现时通过扩展函数进行转换：fun AiResponse.SystemBackContent.toChatResponse() = ChatResponse(text, tone, action)
data class ChatResponse(
    val text: String,
    val tone: String?,
    val action: String?
)

// ChatState.kt
sealed class ChatState {
    object Idle : ChatState()
    object Validating : ChatState()
    data class Filtered(val text: String) : ChatState()
    object Thinking : ChatState()
    data class Responding(val response: ChatResponse) : ChatState()
    data class Error(val message: String) : ChatState()
}
```

## 领域层设计

### ChatUseCase

封装完整对话流程：验证 → 思考 → 回复

```kotlin
class ChatUseCase(
    private val aiRepository: AiRepository,
    private val memoryRepository: MemoryRepository,
    private val dialogValidator: DialogValidator
) {
    /**
     * 执行对话流程
     * @param input 用户输入
     * @return 对话状态流，包含用户输入和AI响应用于记忆更新
     */
    fun execute(input: String): Flow<ChatState> = flow {
        // 1. 验证输入有效性
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

    fun clearHistory() = aiRepository.clearHistory()
}
```

**注意：** 记忆更新在 AI 响应返回后异步执行，不阻塞用户看到响应。

### MemoryUseCase

管理记忆的读取、更新、持久化

```kotlin
class MemoryUseCase(
    private val memoryRepository: MemoryRepository
) {
    suspend fun updateAfterConversation(userInput: String, aiResponse: String): Result<Unit> {
        return memoryRepository.updateMemory(userInput, aiResponse)
    }

    fun getMemoryContext(): String = memoryRepository.getMemoryContext()

    fun clearMemory() = memoryRepository.clearMemory()
}
```

### ConfigUseCase

管理运行时配置

```kotlin
class ConfigUseCase(
    private val configRepository: ConfigRepository
) {
    fun loadConfig(): RuntimeApiConfig = configRepository.loadConfig()

    fun saveConfig(config: RuntimeApiConfig) = configRepository.saveConfig(config)

    fun isConfigComplete(): Boolean = configRepository.loadConfig().isComplete()
}
```

### MotionUseCase

管理 Live2D 动作触发

**注意：** 此 UseCase 依赖 `live2d` 模块的 `LAppLive2DManager`，属于基础设施依赖。虽然不是纯领域类，但为了封装 Live2D 操作的复杂性，这种依赖是可接受的。

```kotlin
class MotionUseCase(
    private val live2DManager: LAppLive2DManager
) {
    private var isThinkingMotionPlaying = false

    fun startThinkingMotion() { ... }
    fun stopThinkingMotion() { ... }
    fun playEmotionMotion(action: String) { ... }

    private val emotionMotionMap = mapOf(
        "shy" to 2, "bored" to 3, "innocent" to 4, "happy" to 5,
        "cute" to 6, "surprised" to 7, "excited" to 8,
        "angry" to 9, "sad" to 10, "love" to 11
    )
}
```

## UI 层设计

### MainViewModel

管理 UI 状态、处理用户交互、协调 UseCase

```kotlin
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

    init {
        checkConfigAndStart()
    }

    fun startListening() { ... }
    private fun processUserInput(text: String) { ... }
    private fun speak(text: String, tone: String?) { ... }
    fun onConfigUpdated() { ... }
    fun clearError() { ... }
}
```

### MainUiState

```kotlin
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

### SettingsViewModel

管理设置页面状态

```kotlin
class SettingsViewModel(
    private val configUseCase: ConfigUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        val config = configUseCase.loadConfig()
        _uiState.update { it.copy(config = config) }
    }

    fun saveConfig(config: RuntimeApiConfig) {
        configUseCase.saveConfig(config)
        _uiState.update { it.copy(saved = true) }
    }
}

data class SettingsUiState(
    val config: RuntimeApiConfig? = null,
    val saved: Boolean = false
)
```

## 依赖注入配置

### Koin 模块

```kotlin
// AppModule.kt
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
    single<AiRepository> { AiRepositoryImpl(get(), get()) }
    single<MemoryRepository> { MemoryRepositoryImpl(get()) }
    single<ConfigRepository> { ConfigRepositoryImpl(get()) }

    // Service
    single<AsrService> { AsrServiceImpl() }
    single<TtsService> { TtsServiceImpl() }

    // Validator
    single { DialogValidator() }
}

// NetworkModule.kt
val networkModule = module {
    single { NetworkKit.createHttpClient(30_000L) }
    single { NetworkKit.json }
}
```

### Application 初始化

```kotlin
class HiruApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        CacheManager.init(this)

        startKoin {
            androidContext(this@HiruApplication)
            modules(appModule, networkModule)
        }
    }
}
```

## 测试策略

### 测试覆盖

| 组件 | 测试重点 |
|------|----------|
| **MainViewModel** | 状态转换、错误处理、配置检查 |
| **ChatUseCase** | 对话流程、验证逻辑、错误处理 |
| **MemoryUseCase** | 记忆更新、上下文获取 |
| **ConfigUseCase** | 配置加载/保存、完整性检查 |
| **AiRepository** | API 调用、历史管理 |

### 测试模块

```kotlin
val testAppModule = module {
    viewModel { MainViewModel(get(), get(), get(), get(), get(), get()) }

    factory { ChatUseCase(get(), get(), get()) }
    factory { MemoryUseCase(get()) }
    factory { ConfigUseCase(get()) }

    single<AiRepository> { FakeAiRepository() }
    single<MemoryRepository> { FakeMemoryRepository() }
    single<ConfigRepository> { FakeConfigRepository() }
    single<AsrService> { FakeAsrService() }
    single<TtsService> { FakeTtsService() }
    single { FakeDialogValidator() }
}

// Fake implementations
class FakeDialogValidator : DialogValidator() {
    var validateResult = true
    override suspend fun validate(text: String): Boolean = validateResult
}

class FakeAiRepository : AiRepository {
    var chatResponse = ChatResponse("test response", null, null)
    override suspend fun chat(input: String, memoryContext: String?) = Result.success(chatResponse)
    override fun clearHistory() {}
}

class FakeMemoryRepository : MemoryRepository {
    private var memory = ""
    override fun getMemory() = memory
    override fun getMemoryContext() = if (memory.isEmpty()) "" else "【关于用户的记忆】$memory"
    override suspend fun updateMemory(userInput: String, aiResponse: String): Result<Unit> {
        memory = "测试记忆"
        return Result.success(Unit)
    }
    override fun clearMemory() { memory = "" }
}

class FakeConfigRepository : ConfigRepository {
    var config = RuntimeApiConfig()
    override fun loadConfig() = config
    override fun saveConfig(newConfig: RuntimeApiConfig) { config = newConfig }
}

class FakeAsrService : AsrService {
    override fun startRecognition(callback: AsrService.AsrCallback) {}
    override fun stopRecognition() {}
    override fun release() {}
}

class FakeTtsService : TtsService {
    override fun speak(text: String, emotion: String?, callback: TtsService.TtsCallback) {
        callback.onPlayStart()
        callback.onPlayComplete()
    }
    override fun stop() {}
    override fun release() {}
}
```

## 迁移策略

1. **添加依赖** - Koin、Lifecycle、ViewModel 相关依赖
2. **创建接口** - 定义 Repository 和 Service 接口
3. **实现接口** - 将现有 Manager 类适配为接口实现
4. **创建 UseCase** - 从 Manager 中提取业务逻辑
5. **创建 ViewModel** - 管理 UI 状态
6. **重构 Activity** - 移除业务逻辑，只保留 UI 操作
7. **配置 Koin** - 组装依赖图
8. **编写测试** - 为核心组件添加单元测试

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 重构过程中功能回归 | 每个步骤完成后进行手动测试 |
| Live2D 集成复杂 | MotionUseCase 封装，保持接口简单 |
| 单例状态迁移 | 使用 Koin 的 single 作用域保持单例语义 |
