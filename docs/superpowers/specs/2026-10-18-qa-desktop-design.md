# Electron + Spring Boot 问答桌面应用设计

## 目标

从零构建一个 Electron + React + Spring Boot 的桌面端 AI 问答应用，满足考核任务中的进程通信、REST 调用、AI 集成、Markdown 展示、历史记录、错误处理和重试要求。

## 明确边界

- UI 只参考 OpenAI/ChatGPT 网页端的布局层级、间距、颜色层级和交互习惯。
- 默认浅色主题，同时支持深色主题；主题切换不改变页面布局。
- 不复制 OpenAI 品牌名称、Logo、官方图标、具体文案或素材。
- 第一阶段不实现登录、联网同步、支付、模型市场、文件上传、插件市场或管理后台。
- AI 方案采用 Function Calling，至少提供 `calculate` 和 `search_latest_news` 两个工具。

## 架构

应用由 Electron 桌面端和 Spring Boot 后端组成。Electron 分为主进程、Preload 和 React 渲染进程：渲染进程只调用 Preload 暴露的白名单 API；主进程管理窗口、IPC、HTTP、重试和日志；Spring Boot 负责请求校验、上下文组装、AI 调用、工具执行、缓存和统一异常处理。

```text
React Renderer ⇄ Preload ⇄ Electron Main ⇄ HTTP/REST ⇄ Spring Boot ⇄ AI Provider
```

## UI 规范

- 左侧固定会话栏，中间居中的对话内容区，底部固定输入框。
- 对话区包含空状态、用户消息、助手消息、加载状态和错误重试状态。
- 助手答案使用 Markdown 渲染，支持标题、列表、引用、代码块和表格。
- 浅色主题默认使用白色主区域、浅灰侧栏和深色文字；深色主题通过 CSS 变量切换。
- 只实现考核所需页面，不自行扩展导航和业务页面。

## 关键接口

### HTTP

- `POST /api/v1/questions`：接收会话 ID、问题和历史上下文，返回 Markdown 答案及工具调用信息。
- `GET /api/v1/health`：返回后端和 AI 服务健康状态。

### IPC

- `question:ask`
- `history:list`
- `history:save`
- `history:delete`
- `history:clear`
- `system:get-version`
- `system:check-backend`

## 安全约束

- Electron 使用 `contextIsolation: true`、`nodeIntegration: false`、Preload 白名单。
- 渲染进程不能直接访问 Node.js、文件系统或 AI Key。
- AI Key 只从 Spring Boot 环境变量读取。
- 后端限制请求长度、请求体大小和工具白名单；统一脱敏异常。

## 数据存储

第一版通过 `HistoryRepository` 抽象本地历史存储，采用 JSON 文件实现；后续可替换 SQLite 而不修改 UI 和问答服务。

## 错误与重试

Electron 对连接失败、超时和 502/503/504 采用最多三次指数退避；参数校验、鉴权和工具参数错误不自动重试。前端显示可理解的错误说明和重试按钮。

## 验收闭环

启动桌面应用后，用户可以创建会话、发送问题，问题经 IPC 和 HTTP 到达 Spring Boot，AI 可触发两个工具之一，返回 Markdown 答案，历史记录被保存；后端关闭时 UI 显示错误并可重试；浅色/深色主题均可正常使用。
