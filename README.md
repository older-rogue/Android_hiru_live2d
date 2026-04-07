# Hiru - AI 虚拟伴侣

一款基于 Live2D 和大语言模型的 Android 虚拟伴侣应用，支持语音对话和情感交互。

## 功能特性

- **Live2D 虚拟形象** - 可交互的 2D 虚拟角色，支持触摸交互和动作播放
- **语音对话** - 完整的语音识别 (ASR) → AI 对话 → 语音合成 (TTS) 流程
- **情感交互** - AI 回复带有情感标签，可触发不同的 Live2D 动作和表情
- **记忆系统** - 支持对话记忆，让 AI 记住与用户的互动
- **连续对话** - 自动语音识别，无需手动触发

## 技术架构

- **架构模式**: MVVM + Clean Architecture
- **UI**: Jetpack Compose + ViewBinding
- **依赖注入**: Koin
- **网络**: OkHttp + Kotlin Serialization
- **语音引擎**: 字节跳动 Speech Engine

### 模块结构

```
├── app/                    # 主应用模块
│   ├── ui/                 # UI 层 (Activity, ViewModel)
│   ├── domain/             # 业务逻辑层 (UseCase, Model)
│   ├── data/               # 数据层 (Repository, Service)
│   ├── ai/                 # AI 对话客户端
│   └── tts/                # 语音处理
└── live2d/                 # Live2D 渲染模块
```

## 演示视频

[演示视频](./Screen_recording_20260407_162320.mp4)

## 配置说明

首次启动应用需要配置：

1. AI 服务 API 地址和密钥(支持openAI协议均可)
2. 语音服务 AppID 和 Token(豆包语音识别与合成，注册测试账号即可体验) 
```
        豆包地址： "wss://openspeech.bytedance.com"
        
        语音识别接口： "/api/v3/sauc/bigmodel_async"
        语音识别资源： "volc.bigasr.sauc.duration"
        
        语音合成接口： "/api/v3/tts/bidirection"
        语音合成资源： "seed-tts-2.0"
```

在设置页面填写配置后即可使用。

## 许可证

本项目 Live2D 相关代码遵循 [Live2D Open Software License](http://live2d.com/eula/live2d-open-software-license-agreement_en.html)。
