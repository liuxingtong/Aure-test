# 系统架构

```text
React UI
  │ contextBridge / IPC
Electron Main Process
  │ HTTP + retry + timeout
Spring Boot REST API
  ├─ QuestionCache / Metrics / RequestLoggingFilter
  ├─ ToolCallingService
  │   ├─ calculate
  │   └─ search_latest_news
  └─ AiClient
      ├─ DemoAiClient
      └─ OpenAiCompatibleClient (DeepSeek/OpenAI-compatible)
```

Electron Renderer 不接触 Node.js 或 API Key。API Key 只在 Spring Boot 进程环境变量中读取，日志过滤问题内容、答案和密钥。会话历史存储在 Electron 用户数据目录的 `conversations.json`。
