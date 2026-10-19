# 系统架构

```text
React UI
  │ contextBridge / IPC + backend status
Electron Main Process
  ├─ BackendProcessManager
  │    ├─ spawn Spring Boot/JAR + platform Java Runtime
  │    ├─ health polling / lifecycle / log rotation
  │    └─ graceful shutdown + single-instance guard
  │
  │ HTTP + retry + timeout
Spring Boot REST API
  ├─ QuestionCache / Metrics / RequestLoggingFilter
  ├─ ToolCallingService
  │   ├─ calculate
  │   └─ search_latest_news
  └─ AiClient
      ├─ DemoAiClient
      └─ ProviderAdapterAiClient
          ├─ DeepSeekProviderAdapter (Chat Completions)
          ├─ OpenAiProviderAdapter (Chat Completions)
          └─ OpenAiCompatibleProviderAdapter (Chat Completions)
```

`ProviderAdapter` 是后端 AI 协议边界。`AI_PROVIDER` 负责选择适配器，统一输入历史消息与问题、输出规范化 Markdown；新增 Responses API、Anthropic Messages API 或 Ollama 原生协议时，只需增加适配器并注册 provider，不改变工具调用、缓存、记忆上下文和 Electron 重启链路。

Electron Renderer 不接触 Node.js 或 API Key。API Key 只在 Spring Boot 进程环境变量中读取，日志过滤问题内容、答案和密钥。会话历史存储在 Electron 用户数据目录的 `conversations.json`。

正式版资源位于 Electron `resources/backend` 和 `resources/jre/<platform>`，用户不需要手动启动 Java 或 Spring Boot。启动时 UI 先显示后端状态，健康检查通过后才开放发送；失败时提供重新启动服务入口。后端日志位于用户数据目录 `logs/backend.log`，超过 2 MiB 时轮转为 `.1`。
