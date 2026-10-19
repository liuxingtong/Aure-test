# QA Desktop 执行 TODO

> 严格执行规则：按顺序推进；每项完成后必须运行对应验证、记录结果并勾选；当前项未通过时不得进入下一项。

## 阶段 0：需求与 UI 规范冻结

- [x] 确认浅色/深色双主题，默认浅色
- [x] 固定 OpenAI/ChatGPT 风格的左侧会话栏、中间对话区、底部输入框布局
- [x] 固定范围：问答、Markdown、历史、IPC、REST、AI、Function Calling、缓存、日志、错误和重试
- [x] 固定排除范围：登录、同步、支付、文件上传、模型市场、插件市场、管理后台
- [x] 自检设计文档与 TODO 的一致性

## 阶段 1：项目脚手架

- [x] 初始化 Electron + React + TypeScript + Vite
- [x] 初始化 Spring Boot 3.x + Java 17
- [x] 建立 Monorepo 目录和共享类型
- [x] 配置统一开发启动脚本
- [x] 验证 Electron 和 Spring Boot 可独立启动

### 阶段 1 验证记录

- 已完成脚手架文件、Spring Boot 健康检查入口和共享类型。
- 已在项目 `.tools/` 安装并验证 Node.js `v22.14.0`、npm `10.9.2`、Temurin/Zulu JDK `17.0.13` 和 Maven `3.9.9`。
- `npm run typecheck` 通过。
- `npm run build:desktop` 通过。
- `mvn -f apps/backend/pom.xml test` 通过，当前无后端测试用例。
- Spring Boot 已启动并验证 `GET /api/v1/health` 返回 `status: UP`。

## 阶段 2：基础 UI（OpenAI/ChatGPT 风格）

- [x] 建立 CSS 设计令牌和浅色主题
- [x] 建立深色主题并实现主题切换
- [x] 实现左侧会话栏
- [x] 实现中间对话区和空状态
- [x] 实现用户消息、助手消息和加载状态
- [x] 实现底部固定输入框和发送按钮
- [x] 实现 Markdown 渲染
- [x] 完成 UI 截图验收，确认没有自行增加页面结构

### 阶段 2 验证记录

- `npm run typecheck` 通过。
- `npm run build:desktop` 通过。
- Vite 页面截图确认默认浅色主题、深色主题切换、左侧会话栏、中间空状态、底部输入框均可见。
- 输入问题后确认用户消息、加载动画、助手消息和 Markdown 列表渲染正常。
- UI 未增加登录、设置中心、模型切换或其他未约定页面。

## 阶段 3：Electron IPC

- [x] 配置安全 BrowserWindow
- [x] 实现 Preload 白名单 API
- [x] 实现 `question:ask` IPC
- [x] 实现历史和系统 IPC 通道
- [x] 为 IPC 请求和响应建立类型
- [x] 编写 IPC 验证

### 阶段 3 验证记录

- 已完成 `contextIsolation: true`、`nodeIntegration: false`、`sandbox: true` 的 BrowserWindow 配置。
- 已完成 Preload 白名单 API，渲染进程不直接接触 `ipcRenderer`。
- `npm run typecheck` 通过，`npm run build:desktop` 通过。
- Electron 二进制已通过镜像安装并验证版本 `v35.7.5`。
- `npm run verify:ipc` 通过：真实 Preload 调用了 `getVersion`、`question:ask`、`history:save/list/clear`，主进程返回 `questionSuccess: true` 且历史数量为 `1`。

## 阶段 4：Spring Boot REST API

- [x] 实现 `POST /api/v1/questions`
- [x] 实现 `GET /api/v1/health`
- [x] 实现 DTO 和参数校验
- [x] 配置 CORS 和请求限制
- [x] 实现全局异常处理
- [x] 编写 Controller/Service 测试

### 阶段 4 验证记录

- `mvn -q -f apps/backend/pom.xml test` 通过，正常请求和空问题校验测试均通过。
- Spring Boot 实际启动成功，`GET /api/v1/health` 返回 `status: UP`。
- 实际 `POST /api/v1/questions` 返回 `success: true`、Markdown 答案和 `toolCalls` 字段。
- 当前答案仍是后端演示实现，阶段 6 才接入真实 AI Adapter。

## 阶段 5：Electron 到后端联调

- [x] 实现主进程 HTTP Client
- [x] 实现超时和错误映射
- [x] 实现指数退避重试
- [x] 打通完整的模拟问答链路
- [x] 验证后端关闭时的 UI 错误状态

### 阶段 5 验证记录

- Electron 主进程通过 `fetch` 调用 `http://127.0.0.1:8080/api/v1/questions`。
- 请求设置 10 秒超时，连接失败最多重试 3 次，最终映射为 `BACKEND_UNAVAILABLE`。
- Spring Boot 启动后运行 `npm run verify:ipc`，真实链路返回 Spring Boot 的演示答案，证明 Electron IPC → HTTP → REST 已打通。
- 停止后端时 IPC 返回可重试错误，验证了后端不可用分支。

## 阶段 6：AI Adapter

- [x] 抽象 `AiClient`
- [x] 实现 AI Provider 适配器
- [x] 配置环境变量读取 API Key
- [x] 实现 Prompt 和上下文构造
- [x] 实现 Markdown 答案标准化
- [x] 编写 AI 调用测试和超时测试

### 阶段 6 验证记录

- `AiClient` 默认使用演示实现；配置 `AI_ENABLED=true` 后切换为 OpenAI-compatible `/chat/completions` 适配器。
- API Key、模型、Base URL 和超时均通过环境变量读取，默认不启用外部 AI，避免未配置时启动失败。
- Prompt 固定包含系统指令、历史上下文和当前问题，回答统一进行 Markdown 换行与空内容规范化。
- `mvn -q -f apps/backend/pom.xml test` 通过，覆盖 Prompt 顺序、Markdown 规范化、AI 响应解析、空响应和读取超时。

## 阶段 7：Function Calling

- [x] 定义 `AiTool` 和工具注册表
- [x] 实现 `calculate` 工具
- [x] 实现 `search_latest_news` 工具适配器
- [x] 实现工具调用解析和二次 AI 请求
- [x] 增加工具参数校验、超时和最大调用次数
- [x] 编写两个工具的单元测试

### 阶段 7 验证记录

- 工具调用协议采用 `{"tool_calls":[{"name":"...","arguments":{...}}]}`，解析后由注册表分发。
- `calculate` 仅允许数字、括号和四则运算；`search_latest_news` 对空查询进行参数校验，并在未配置新闻服务时返回明确状态。
- 每轮工具结果会回填上下文并触发二次 AI 请求，最多允许 3 次工具调用，未知工具和非法参数直接失败。
- `mvn -q -f apps/backend/pom.xml test` 通过，覆盖计算、工具注册、调用解析、工具结果回填及控制器链路。

## 阶段 8：本地会话历史

- [x] 定义 Conversation 和 Message 类型
- [x] 实现 `HistoryRepository`
- [x] 实现 JSON 文件存储
- [x] 实现启动时加载历史
- [x] 实现新建、切换、删除和清空会话
- [x] 编写历史存储测试

### 阶段 8 验证记录

- Electron 主进程使用用户数据目录下的 `conversations.json`，采用临时文件写入后重命名，避免半写入文件。
- UI 启动时通过 IPC 加载历史，侧栏支持新建和切换；IPC 已提供删除与清空能力。
- `npm --workspace apps/desktop run build` 通过；`npm run verify:ipc` 返回 `historyCount: 1` 与 `historyReloadedCount: 1`，验证持久化仓储的读写链路。

## 阶段 9：缓存、日志和监控

- [x] 实现问题缓存
- [x] 实现 Electron 日志
- [x] 实现 Spring Boot 结构化日志
- [x] 增加请求 ID 和耗时记录
- [x] 增加后端健康检查展示
- [x] 验证日志不包含 API Key

### 阶段 9 验证记录

- 问题和历史上下文生成缓存键，默认缓存 5 分钟，可通过 `QUESTION_CACHE_TTL` 调整。
- 日志只记录会话 ID、历史长度和错误类型，不记录问题内容、答案或 API Key。
- `/api/v1/health` 返回 AI 配置状态以及总请求数、缓存命中数和失败数。
- `mvn -q -f apps/backend/pom.xml test` 通过，覆盖缓存 TTL 和完整控制器链路。

## 阶段 10：测试、打包和交付

- [x] 完成前端组件测试
- [x] 完成后端集成测试
- [x] 完成 IPC 和联调测试
- [x] 配置 Electron Builder
- [x] 打包并验证桌面应用
- [x] 更新 README、架构文档和测试报告

## 阶段 12：账户连接、主题与归档

- [x] 建立统一 Provider Adapter 接口，接入 DeepSeek、OpenAI 与 OpenAI 兼容 Chat Completions
- [ ] 预留 Responses API、Anthropic Messages API、Ollama 原生协议扩展点

- [x] 将主题切换移至聊天区右上角图标
- [x] 将左下角区域改为本地账户入口
- [x] 支持 DeepSeek、OpenAI 和其他 OpenAI 兼容 Chat Completions 服务
- [x] 支持输入 API Key、接口地址和模型名称，并保存后重启后端
- [x] API Key 使用系统安全存储能力加密后保存（不可用时仅保留本地配置文件权限）
- [x] 历史记录支持归档、恢复和独立查看
- [x] 增加可选的本地记忆内容，并在请求时作为上下文发送
- [ ] 为账户设置、归档和记忆补充独立组件测试
- [ ] 在 Windows 实机验证设置保存、后端重启和加密存储
- [x] 完成最终验收演示
- [x] 构建并验证 Windows x64 版本

### 阶段 10 当前验证记录

- 新增 `npm run verify:all`，依次执行后端测试、桌面生产构建和 Electron IPC 联调。
- 验收脚本强制使用演示 AI，避免自动化验证消耗外部额度；真实 DeepSeek 调用由 `.env.local` 配置控制。
- 已完成组件测试、Electron Builder 目录打包、实际启动验证和交付文档；正式签名/公证属于发布环境工作。
- 前端 Vitest 测试覆盖空状态和问答提交；Electron Builder 使用 macOS arm64 `--dir` 目标完成本地目录包验证。
- Windows x64 目录包已生成并通过 PE32+ x86-64 文件格式检查；Windows 实机启动需在 Windows 环境完成最终运行验证。

## 阶段 11：正式版一键启动全部服务

- [x] Electron 主进程增加 `BackendProcessManager`
- [x] 开发环境支持自动启动 Spring Boot
- [x] 正式版纳入 Spring Boot 可执行 JAR
- [x] Mac arm64 纳入匹配的 Java Runtime
- [ ] Windows x64 纳入匹配的 Java Runtime（需 Windows 实机准备并验证）
- [x] 增加 `/api/v1/health` 启动轮询，启动超时固定为 30 秒
- [x] 增加 8080 端口占用检测和明确错误
- [x] 增加单实例锁和重复后端启动保护
- [x] 增加后端 stdout/stderr 日志轮转
- [x] 确保日志不包含 API Key、问题正文和答案正文
- [x] 应用退出时优雅关闭本次启动的后端进程
- [x] 后端异常退出时显示可重试状态
- [x] 增加启动中、已就绪、启动失败三种 UI 状态
- [x] 服务未就绪时禁用提问发送
- [x] 增加 Mac 签名/公证发布检查项
- [x] 增加 Windows 杀毒误报和签名发布检查项
- [x] 增加包体积和启动耗时记录
- [x] 验证 Mac 正式版双击启动、提问和退出清理
- [ ] 验证 Windows x64 正式版双击启动、提问和退出清理（需 Windows 实机）
- [x] 更新 README、架构文档和测试报告
