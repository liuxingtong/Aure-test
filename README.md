# yunhe

**yunhe** 是一个本地优先的 AI 问答桌面应用：Electron + React 负责桌面交互，Spring Boot 负责 AI 编排、工具执行和本地数据服务。

它的目标不是做一个浏览器套壳，而是把“桌面 IPC、后端 REST、AI Provider、Function Calling、本地历史和打包运行时”完整串起来：用户双击应用即可使用，不需要手动安装 Java、启动 Maven 或打开终端。

- 默认浅色主题，同时支持深色主题
- 会话、设置、生成的 Markdown 都保存在本机
- 已保存的 API Key 只存在本机安全存储/配置文件中，不会回传到渲染进程
- 后端默认只绑定 `127.0.0.1`

---

## 1. 功能

### 对话与界面

- OpenAI/ChatGPT 风格的布局：左侧会话栏、中间对话区、底部固定输入框
- Markdown 渲染，支持 GFM 表格、任务列表、代码块
- KaTeX 数学公式渲染：行内 `$...$` 和块级 `$$...$$`
- 助手消息支持可展开的“思考过程”（仅当 Provider 返回思考字段时显示）
- 浅色/深色主题切换
- 模型选择器：可在对话输入区或账户设置中切换模型
- 启动时默认进入**新对话**，不会自动选中历史会话；历史记录仍显示在侧栏，点击后才加载对应消息

### 本地历史

- 会话历史保存到 Electron 用户数据目录的 `conversations.json`
- 使用临时文件 + rename 写入，避免半写入文件
- 支持新建、切换、归档、恢复、删除、清空历史
- 删除当前会话会同步清空当前视图
- 附件只保留文件名，不把文档正文反复写进历史文件

### 文档附件

- 通过 Electron 系统文件选择器选择 `.md`、`.txt`、`.json`
- 文件最大 1 MiB，只接受 UTF-8
- 支持一次添加多个文档
- 后端 `read_document` 只读取当前请求中由文件选择器授权的文档，不接受模型提供的任意路径
- 未授权时返回 `DOCUMENT_SELECTION_REQUIRED`，前端据此打开文件选择器

### AI Provider

当前支持三类 Chat Completions 协议：

| Provider | 说明 |
| --- | --- |
| DeepSeek | `https://api.deepseek.com`，支持 `deepseek-chat` / `deepseek-reasoner` |
| OpenAI | `https://api.openai.com/v1` |
| OpenAI 兼容 | Ollama、vLLM、其他兼容 `/chat/completions` 的服务 |

- API Key、Base URL、模型名称可在账户设置中配置
- API Key 在 `safeStorage` 可用时加密后保存，不可用时只依赖本地配置文件权限；界面不会回显已保存的明文
- 历史列表中的模型选择会随请求发送到后端
- `deepseek-reasoner` 等返回 `reasoning_content`/`reasoning`/`reflection` 的模型，前端会展示“已思考”可展开块

### Function Calling 工具

模型通过系统提示词约定的 JSON 协议发起工具调用：

```json
{"tool_calls":[{"name":"calculate","arguments":{"expression":"sqrt(2^10+24^2)*ln(e^3)"}}]}
```

当前内置 5 个工具：

| 工具 | 能力 |
| --- | --- |
| `calculate` | 四则运算、`^`/`**` 幂、阶乘、开方、对数、三角/反三角/双曲函数、取整、`pi/e` 常量、`max/min/pow` |
| `search_latest_news` | 读取 Google News RSS，返回最多 5 条结果，并附 Google/Bing 搜索链接 |
| `create_plan` | 生成固定结构的 Markdown 执行计划，支持 phases/acceptance |
| `generate_markdown` | 生成标题、正文、列表、表格、代码块，并落盘到 `~/Documents/yunhe/` |
| `read_document` | 读取当前请求授权的 md/txt/json 文档，支持多文件与 filename 选择 |

工具执行后的结果会以 **assistant + user 普通消息**回填上下文，而不是伪造 `role: "tool"`。这样可以避免 Chat Completions 对 `tool_call_id` 的校验错误（Real Provider 会返回 422）。工具循环最多 6 次，超过后强制模型直接给出最终答案。

工具错误使用稳定错误码：

- `UNKNOWN_TOOL`
- `INVALID_TOOL_ARGUMENTS`
- `DOCUMENT_SELECTION_REQUIRED`
- `DOCUMENT_NOT_FOUND`
- `DOCUMENT_TOO_LARGE`
- `DOCUMENT_INVALID_ENCODING`
- `TOOL_EXECUTION_FAILED`

### 错误与重试

- Electron 主进程调用后端的超时为 45 秒
- 网络错误、可重试的 5xx/限流响应最多重试 3 次，指数退避
- 参数校验、鉴权、工具参数错误不自动重试
- Provider 错误会映射为可操作提示：
  - `AI_AUTH_ERROR`：API Key 无效或无权限
  - `AI_ENDPOINT_NOT_FOUND`：接口地址/模型不存在
  - `AI_RATE_LIMITED`：请求过于频繁
  - `AI_REQUEST_INVALID`：模型或输入被 Provider 拒绝
  - `AI_CONNECTION_ERROR`：无法连接 AI 服务
  - `AI_SERVICE_UNAVAILABLE`：Provider 5xx
- 后端统一返回 `{success, data, error}` 结构，前端可直接显示错误文案

---

## 2. 技术路径

### 总体架构

```text
React Renderer
  │  contextBridge + typed IPC（仅白名单 API）
Electron Main
  ├─ HistoryRepository（conversations.json）
  ├─ SettingsStore（safeStorage 加密 API Key）
  ├─ BackendProcessManager（启动/健康检查/退出清理）
  └─ askBackend（HTTP + 超时 + 重试）
        │
Spring Boot Backend（127.0.0.1:8080）
  ├─ QuestionController / GlobalExceptionHandler
  ├─ QuestionService / QuestionCache / QaMetrics
  ├─ ToolCallingService / ToolRegistry
  ├─ CalculateTool / SearchLatestNewsTool / PlanTool
  ├─ MarkdownDocumentTool / ReadDocumentTool
  └─ ProviderAdapterAiClient
        ├─ DeepSeekProviderAdapter
        ├─ OpenAiProviderAdapter
        └─ OpenAiCompatibleProviderAdapter
```

### Electron 三层

**主进程**

- 创建并管理 `BrowserWindow`
- 管理应用生命周期、单实例锁、退出清理
- 通过 `BackendProcessManager` 启动 Spring Boot
- 轮询 `/api/v1/health`，默认 30 秒超时
- 通过 `fetch` 调用后端，处理超时、重试和错误映射
- 把后端 stdout/stderr 写入用户数据目录的轮转日志，过滤 API Key

**Preload**

- `contextBridge` 暴露窄接口
- 渲染进程不能直接访问 `ipcRenderer`、Node.js、文件系统或 API Key
- IPC 白名单包括：问答、历史增删改查、归档、设置、模型列表、后端状态、重启服务、文件选择、打开外部链接

**Renderer**

- React 19 + Vite
- 只通过 `window.electronAPI` 与主进程通信
- Markdown/KaTeX 渲染、主题、历史侧栏、附件、思考块、错误重试

安全配置：

```ts
contextIsolation: true
nodeIntegration: false
sandbox: true
```

### Spring Boot 后端

- Java 17 + Spring Boot 3.4.1
- REST API：
  - `POST /api/v1/questions`
  - `GET /api/v1/health`
- Bean Validation 校验 `conversationId`、`question`、history 数量和单条长度
- `RequestSizeFilter` 限制请求体大小，413 使用统一 JSON 结构
- `WebConfig` 允许本地开发端口 CORS
- 默认 `server.address=127.0.0.1`
- `QuestionService` 负责缓存、指标和调用工具链
- `QuestionCache` 默认 TTL 5 分钟，最多 256 条；带文档的请求绕过缓存
- `QaMetrics` 统计总问题数、缓存命中数、失败数
- `RequestLoggingFilter` 记录请求 ID、路径、状态码和耗时，不记录问题正文

### AI Adapter

`ProviderAdapter` 是 Provider 协议边界：

- `ProviderAdapterAiClient` 根据 `AI_PROVIDER` 选择适配器
- `DeepSeekProviderAdapter`、`OpenAiProviderAdapter`、`OpenAiCompatibleProviderAdapter` 共用 `AbstractChatCompletionsAdapter`
- 解析 `choices[0].message.content`
- 提取 `reasoning_content` / `reasoning` / `reflection` 作为思考字段
- 使用 `RestClient` 发送 `/chat/completions` 请求，超时由 `AI_TIMEOUT` 控制

### 工具调用链路

1. `PromptBuilder` 把系统指令、历史、当前问题组装成 messages
2. 模型输出普通答案或 `tool_calls` JSON
3. `ToolCallParser` 解析工具调用；对模型漏写 `]` 的畸形 JSON 做容错恢复
4. `ToolRegistry` 根据工具名分发，未知工具/参数错误映射为错误码
5. `ToolCallingService` 把工具结果作为 assistant + user 消息回填
6. 再次请求模型，直到模型给出最终答案或用完 6 次工具额度
7. 最终答案、工具调用列表和 thinking 返回给前端

`calculate` 不依赖 GraalJS/Nashorn，而是使用自带的递归下降解析器：

- 体积小、启动快
- 不依赖宿主 JDK 模块或 JS 引擎
- 只接受白名单字符和预定义函数，没有任意代码执行入口

### 本地数据与安全

| 数据 | 位置 | 说明 |
| --- | --- | --- |
| 会话历史 | 用户数据目录 `conversations.json` | 原子写入；损坏文件会先备份 |
| 设置 | 用户数据目录 `settings.json` | API Key 在 `safeStorage` 可用时加密，否则依靠文件权限 |
| 后端日志 | 用户数据目录 `logs/backend.log` | 2 MiB 轮转，过滤密钥 |
| Trace | 用户数据目录 `traces/YYYY-MM-DD.jsonl` | 默认脱敏，只记录长度/条数 |
| 生成的 Markdown | `~/Documents/yunhe/` | 文件名带日期后缀 |

- 已保存的 API Key 不会回传到 Renderer；用户输入时仅存在于设置表单，保存后由主进程加密存储并注入后端环境变量
- `shell:open-external` 只允许 `http/https/file`；`file:` 仅允许打开 `~/Documents/yunhe` 下的文件
- Trace 默认不写问题、答案、文档正文；需要完整内容时设置 `AI_TRACE_CONTENT=true`
- 后端只监听回环地址，避免局域网访问

### 启动与打包

开发模式：

- `start-dev.sh` 启动 Vite + Electron
- Electron 主进程自动执行 `mvn spring-boot:run` 启动后端
- 关闭 Electron 时结束本次启动的 Java 进程

正式版：

- 后端打成 Spring Boot 可执行 JAR
- macOS 使用 `jlink` 生成最小 JRE；Windows 使用 Adoptium JRE 17 x64
- `electron-builder` 把 `resources/backend` 和 `resources/jre/<platform>` 放进应用
- 主进程启动 `java -jar qa-backend-0.1.0.jar`
- 健康检查通过后开放提问
- `files` 排除 `node_modules` 和 sourcemap，渲染资源由 Vite 打进 `dist`

---

## 3. 目录结构

```text
apps/
  desktop/
    electron/          Electron 主进程、BackendProcessManager、IPC、历史/设置存储
    preload/           contextBridge 白名单
    src/               React Renderer、Markdown/KaTeX、样式
    assets/            应用图标
  backend/
    src/main/java/     Spring Boot REST、AI Adapter、工具、缓存、监控
    src/test/java/     后端单元测试和控制器测试
packages/
  shared-types/        前后端共享 TypeScript 类型
scripts/               开发、启动、资源准备、打包验证脚本
docs/                  架构设计和验收报告
```

---

## 4. 开发运行

### 环境

- Node.js 22+
- Java 17+
- Maven 3.9+
- macOS arm64（当前已验证）；Windows x64 已构建，实机运行待验证

### 配置

复制环境模板并填写 API Key：

```bash
cp .env.local.example .env.local
```

`.env.local` 已被 Git 忽略，不应提交。

也可以在应用左下角“本地账户”中填写 API Key、接口地址和模型；保存后应用会重启后端。

### 启动

```bash
npm install
./scripts/start-dev.sh
```

等价于：

```bash
npm run dev:desktop
```

脚本会启动 Vite、Electron，并由 Electron 主进程自动启动 Spring Boot；关闭 Electron 时会一并结束后端。

### 测试

```bash
npm run typecheck
npm --workspace apps/desktop run test
mvn -f apps/backend/pom.xml test
npm run build:desktop
```

当前验证基线：

- 后端 50 个测试通过
- 前端 25 个测试通过
- `typecheck`、生产构建通过

---

## 5. 打包与交付

### macOS arm64

```bash
export QA_JRE_SOURCE=/path/to/java-17-jdk
./scripts/prepare-backend-runtime.sh
npm --workspace apps/desktop run package:dir
```

- `prepare-backend-runtime.sh` 会用 `jlink` 生成最小 JRE（约 57 MB）
- 产物：`apps/desktop/release/mac-arm64/yunhe.app`
- 便携 zip：`apps/desktop/release/mac-arm64/yunhe-0.1.0-arm64.zip`
- 正式发布仍需 Apple Developer 签名和公证

### Windows x64

```powershell
$env:QA_JRE_SOURCE = 'C:\path\to\java-17-jre'
.\scripts\build-windows.ps1
```

或：

```bash
npm --workspace apps/desktop run package:win
npm --workspace apps/desktop run package:win:installer
```

- 免安装目录：`apps/desktop/release/win-unpacked/yunhe.exe`
- 便携 zip：`apps/desktop/release/yunhe-0.1.0-win-x64.zip`
- NSIS 安装包：`apps/desktop/release/yunhe-0.1.0-setup-x64.exe`
- Windows 包需要 `runtime/jre/win-x64` 下的 Java 17 Runtime
- 正式发布仍需代码签名，并在目标机器检查杀毒软件误报

### 体积说明

| 组成 | 大小（约） |
| --- | --- |
| Electron 运行时 | 250 MB |
| macOS 最小 JRE | 57–61 MB |
| Windows JRE 17 x64 | 126 MB |
| Spring Boot JAR | 22 MB |

安装包体积是正常的：Electron 本身约 250 MB，加上 Java Runtime 和 Spring Boot 后，macOS 目录包约 344 MB、zip 约 163 MB；Windows 目录包约 434 MB、安装包约 128 MB。`app.asar` 只包含 `dist`、`dist-electron` 和 `package.json`，不包含 `node_modules`、sourcemap、日志或缓存。

---

## 6. HTTP API

### `POST /api/v1/questions`

请求示例：

```json
{
  "conversationId": "conv-1",
  "question": "用 calculate 工具计算 sqrt(2^10+24^2)*ln(e^3)",
  "history": [
    {"id":"m1","role":"user","content":"你好","createdAt":"2026-10-19T00:00:00Z"}
  ],
  "model": "deepseek-reasoner",
  "documents": [
    {"name":"notes.md","extension":"md","content":"# Notes"}
  ]
}
```

响应：

```json
{
  "success": true,
  "data": {
    "conversationId": "conv-1",
    "messageId": "...",
    "question": "...",
    "answer": "最终 Markdown 答案",
    "toolCalls": ["calculate"],
    "createdAt": "2026-10-19T00:00:00Z",
    "thinking": "可选思考内容"
  },
  "error": null
}
```

失败时：

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AI_AUTH_ERROR",
    "message": "API Key 无效或没有访问权限，请在账户设置中检查。",
    "retryable": false
  }
}
```

### `GET /api/v1/health`

返回后端状态、AI 配置状态和基础指标：

```json
{
  "status": "UP",
  "aiService": "CONFIGURED",
  "version": "0.1.0",
  "metrics": {
    "totalQuestions": 3,
    "cacheHits": 1,
    "failures": 0
  }
}
```

---

## 7. 已知限制

- Function Calling 使用自定义 JSON 协议，而不是 Provider 原生 `tools` 字段；模型必须按系统提示词输出 JSON
- Windows x64 包已完成构建和 PE 检查，但尚未在 Windows 实机运行验证
- Windows 安装包未签名，首次运行可能触发 SmartScreen
- macOS 包未签名/未公证；当前交付使用 zip 便携包，DMG 需要在普通终端环境用 `hdiutil` 生成
- 缓存命中只保存答案，可能丢失当次的 `thinking` 和 `toolCalls`
- 不包含登录、云同步、联网账户、支付、插件市场、文件上传服务端等能力
- 账户设置中已移除“启用记忆”，不会向请求注入记忆上下文
- Trace 默认脱敏；设置 `AI_TRACE_CONTENT=true` 才会记录完整问题、答案、文档和工具参数

---

**版本**：0.1.0 · **更新**：2026-10-19
