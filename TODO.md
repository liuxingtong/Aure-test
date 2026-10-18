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

- [ ] 实现 `POST /api/v1/questions`
- [ ] 实现 `GET /api/v1/health`
- [ ] 实现 DTO 和参数校验
- [ ] 配置 CORS 和请求限制
- [ ] 实现全局异常处理
- [ ] 编写 Controller/Service 测试

## 阶段 5：Electron 到后端联调

- [ ] 实现主进程 HTTP Client
- [ ] 实现超时和错误映射
- [ ] 实现指数退避重试
- [ ] 打通完整的模拟问答链路
- [ ] 验证后端关闭时的 UI 错误状态

## 阶段 6：AI Adapter

- [ ] 抽象 `AiClient`
- [ ] 实现 AI Provider 适配器
- [ ] 配置环境变量读取 API Key
- [ ] 实现 Prompt 和上下文构造
- [ ] 实现 Markdown 答案标准化
- [ ] 编写 AI 调用测试和超时测试

## 阶段 7：Function Calling

- [ ] 定义 `AiTool` 和工具注册表
- [ ] 实现 `calculate` 工具
- [ ] 实现 `search_latest_news` 工具适配器
- [ ] 实现工具调用解析和二次 AI 请求
- [ ] 增加工具参数校验、超时和最大调用次数
- [ ] 编写两个工具的单元测试

## 阶段 8：本地会话历史

- [ ] 定义 Conversation 和 Message 类型
- [ ] 实现 `HistoryRepository`
- [ ] 实现 JSON 文件存储
- [ ] 实现启动时加载历史
- [ ] 实现新建、切换、删除和清空会话
- [ ] 编写历史存储测试

## 阶段 9：缓存、日志和监控

- [ ] 实现问题缓存
- [ ] 实现 Electron 日志
- [ ] 实现 Spring Boot 结构化日志
- [ ] 增加请求 ID 和耗时记录
- [ ] 增加后端健康检查展示
- [ ] 验证日志不包含 API Key

## 阶段 10：测试、打包和交付

- [ ] 完成前端组件测试
- [ ] 完成后端集成测试
- [ ] 完成 IPC 和联调测试
- [ ] 配置 Electron Builder
- [ ] 打包并验证桌面应用
- [ ] 更新 README、架构文档和测试报告
- [ ] 完成最终验收演示
