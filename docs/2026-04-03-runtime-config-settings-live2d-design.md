# 运行时配置设置与 Live2D 眼球跟随增强设计

## 背景

当前应用存在三个问题：

1. 主界面在运行过程中持续显示状态文字，不符合当前产品期望。
2. `AppConfig` 中的 AI / ASR / TTS 配置硬编码在代码里，无法由用户在应用内修改，也无法在保存后即时生效。
3. Live2D 触摸拖动时眼球跟随幅度偏小，视觉反馈不明显，但不能改变现有的跟随方向。

本次改动目标是在尽量不改动现有主界面结构的前提下，补齐运行时配置能力，并增强 Live2D 眼球跟随体验。

## 目标

- 取消主界面上的运行时文字提示。
- 在主界面右上角增加设置按钮。
- 将原先 `AppConfig` 中所有实际业务配置项改为运行时可配置项。
- 配置项不再提供业务默认值；未配置时视为缺失。
- 配置保存到共享参数中，应用初始化和配置变更后都重新读取并重新初始化相关模块。
- 应用启动时若关键配置缺失，直接弹出设置对话框引导用户填写。
- 增大 Live2D 眼球跟随幅度，同时保持当前左右上下的跟随方向不变。

## 非目标

- 不新增独立的设置页面，优先使用主界面上的弹窗配置。
- 不改变现有 Live2D 头部、身体转向方向映射规则。
- 不重构整套语音链路架构，只在现有类上增加必要的重初始化能力。
- 不取消权限和错误 `Toast`，仅取消主界面上的文字提示。

## 方案概览

### 1. 主界面 UI 调整

保留现有 `activity_main.xml` 的全屏 Live2D 布局，在右上角新增一个设置按钮。原有的 `tv_status` 和 `tv_user_text` 不再承担运行时文本展示职责，直接隐藏。

设置按钮点击后弹出一个配置对话框，表单包含以下配置：

- AI
  - `baseUrl`
  - `apiKey`
  - `model`
  - `timeoutMs`
  - `systemPrompt`
- ASR
  - `appId`
  - `token`
  - `address`
  - `uri`
  - `resourceId`
- TTS
  - `appId`
  - `token`
  - `address`
  - `uri`
  - `resourceId`

对话框支持保存和取消。保存时做必填校验，校验通过后写入共享参数并通知主流程重初始化。

### 2. 启动时强制配置

应用启动时先初始化缓存，再读取运行时配置。

如果关键配置缺失，则：

- 不自动启动语音识别。
- 立即弹出设置对话框。
- 对话框在首次缺失场景下不可忽略，用户需要填写并保存后才进入正常工作流。

关键配置定义为：

- AI: `baseUrl`、`apiKey`、`model`
- ASR: `appId`、`token`、`address`、`uri`、`resourceId`
- TTS: `appId`、`token`、`address`、`uri`、`resourceId`

`timeoutMs` 与 `systemPrompt` 也改为可配置，但允许在表单校验时用“不能为空”策略统一处理，避免隐藏默认值。

### 3. 配置存储与读取模型

新增统一的运行时配置存储层，职责如下：

- 从 `SharedPreferences` 读取完整配置。
- 提供完整配置对象给调用方。
- 保存配置时一次性覆盖所有字段。
- 提供“配置是否完整”的判定能力。

该层不提供业务默认值。读取不到值时返回空字符串或空值状态，由上层明确处理缺失逻辑。

为了避免继续依赖硬编码常量，`AppConfig` 调整为配置访问入口，而不再保存真实配置值。调用方通过 `AppConfig` 的读取接口或新的配置仓库获取最新值。

### 4. 初始化与重初始化

现有多个类在初始化时会直接读取配置或构建网络客户端，需要统一支持“保存后立即生效”。

处理方式如下：

- `MainActivity`
  - 启动时初始化缓存和配置仓库。
  - 首次读取配置并决定是否自动启动语音链路。
  - 设置保存成功后触发一次“重新应用配置”。
- `VoiceChatManager`
  - 增加基于最新配置的重新初始化入口。
  - 重新创建 `AiClient`，并重置依赖配置的语音模块。
- `AsrManager` / `TtsManager`
  - 现有引擎初始化后会缓存配置，需要增加显式重置逻辑。
  - 当配置变更时，释放旧引擎并按新配置重新初始化。
- `DialogValidator` / `MemoryManager`
  - 当前直接读取 `AppConfig` 常量，需要切换为运行时配置来源，确保配置变更后新的请求也生效。

### 5. Live2D 眼球跟随增强

当前 `LAppModel.update()` 中：

- 头部转向使用 `AngleX / AngleY / AngleZ`
- 身体转向使用 `BodyAngleX`
- 眼球转向使用 `EyeBallX / EyeBallY`

本次只调整眼球参数的强度，不改变参数符号关系，也不改变头部和身体的映射方向。实现上：

- 将眼球跟随增益提取为清晰常量，例如 `EYE_BALL_DRAG_MULTIPLIER`。
- 在 `addParameterValue(idParamEyeBallX, ...)` 和 `addParameterValue(idParamEyeBallY, ...)` 上放大幅度。
- 保持 `dragX` 和 `dragY` 的正负号不变，确保视觉方向不反转。

同时修正 `LAppView.onTouchesMoved()` 中拖动坐标使用时机：先更新当前触点，再将最新坐标转换为视图坐标并传给 `onDrag`，避免使用上一次触点导致跟手偏弱和迟滞。

## 组件改动

预计涉及以下文件：

- `app/src/main/res/layout/activity_main.xml`
  - 隐藏运行时文字控件
  - 增加右上角设置按钮
- `app/src/main/java/com/zx/hiru/MainActivity.kt`
  - 启动配置检查
  - 设置弹窗打开/保存逻辑
  - 配置变更后的重新初始化
- `app/src/main/java/com/zx/hiru/config/AppConfig.kt`
  - 改为运行时配置访问入口
- `app/src/main/java/com/zx/hiru/cache/CacheKeys.kt`
  - 补齐所有配置项键名
- `app/src/main/java/com/zx/hiru/cache/CacheManager.kt`
  - 复用现有共享参数能力
- `app/src/main/java/com/zx/hiru/ai/AiConfig.kt`
  - 取消硬编码默认配置来源
- `app/src/main/java/com/zx/hiru/tts/VoiceChatManager.kt`
  - 支持配置重建
- `app/src/main/java/com/zx/hiru/tts/AsrManager.kt`
  - 运行时读取配置，支持重置引擎
- `app/src/main/java/com/zx/hiru/tts/TtsManager.kt`
  - 运行时读取配置，支持重置引擎
- `app/src/main/java/com/zx/hiru/ai/DialogValidator.kt`
  - 运行时读取 AI 配置
- `app/src/main/java/com/zx/hiru/ai/MemoryManager.kt`
  - 运行时读取 AI 配置
- `live2d/src/main/java/com/zx/live2d/LAppView.java`
  - 修正触摸移动坐标使用时机
- `live2d/src/main/java/com/zx/live2d/LAppModel.java`
  - 放大眼球跟随幅度

如果表单布局较复杂，可新增一个简单对话框布局文件用于设置内容承载。

## 数据流

### 启动流程

1. `MainActivity` 启动。
2. 初始化 `CacheManager` 和配置仓库。
3. 读取运行时配置。
4. 如果配置不完整，立刻弹出设置对话框并阻止自动语音初始化。
5. 如果配置完整，初始化 `VoiceChatManager` 并按现有逻辑启动监听。

### 保存流程

1. 用户点击右上角设置按钮。
2. 弹出配置对话框，显示当前共享参数中的值。
3. 用户编辑并点击保存。
4. 执行字段校验。
5. 校验通过后写入共享参数。
6. `MainActivity` 触发重新应用配置。
7. `VoiceChatManager`、`AsrManager`、`TtsManager`、AI 请求相关模块读取最新配置。
8. 若当前处于可工作状态，则重新开始监听。

## 错误处理

- 配置缺失：
  - 启动时直接弹设置框。
  - 保存时给出 `Toast`，指出未填写项。
- 配置变更时重初始化失败：
  - 保留 `Toast` 报错。
  - 不在主界面显示状态文字。
- Live2D 拖动增强：
  - 若模型缺少眼球参数，保持当前 SDK 行为，不额外报错。

## 测试策略

遵循 TDD，先补最小失败测试，再写实现。

### 单元测试

- 配置仓库
  - 空缓存读取时返回“不完整配置”
  - 保存后可以读取到完整配置
  - 二次保存会覆盖旧值
- 配置完整性判定
  - 任一关键字段缺失时返回不完整
  - 全字段存在时返回完整

### 集成验证

- 启动时无配置，设置框自动弹出
- 保存配置后会重新初始化并允许进入监听流程
- 设置修改后新请求使用最新配置
- 主界面不再显示状态文字
- Live2D 拖动时眼球跟随更明显，方向与改动前一致

## 风险与约束

- 当前语音引擎封装为单例，若重置不彻底，可能出现旧引擎残留状态。实现时需要明确释放顺序。
- `DialogValidator` 和 `MemoryManager` 当前直接依赖旧配置入口，若遗漏切换，保存后的新配置不会完全生效。
- 设置对话框字段较多，需要控制布局滚动和输入类型，避免小屏幕可用性问题。

## 推荐实现顺序

1. 建立运行时配置数据模型、存储仓库和完整性校验。
2. 补测试，验证配置读写与完整性判定。
3. 接入 `AppConfig`、`AiConfig`、`DialogValidator`、`MemoryManager`。
4. 接入 `AsrManager`、`TtsManager`、`VoiceChatManager` 的重初始化逻辑。
5. 修改主界面布局与设置弹窗。
6. 修改启动缺失配置时的强制弹窗流程。
7. 修改 Live2D 触摸移动与眼球跟随幅度。
8. 执行编译和必要测试验证。
