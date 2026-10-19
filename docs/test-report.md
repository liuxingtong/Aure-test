# 阶段 10 验收报告

日期：2026-10-18

## 2026-10-19 修复复验

- 后端 `mvn test`：50 个用例全部通过。
- 前端 `vitest run`：25 个用例全部通过；`typecheck` 和 `build:desktop` 通过。
- 真实 DeepSeek 链路验证：问题“能否写一条数学公式并调用工具计算”返回 `success: true`、`toolCalls: ["calculate"]` 和最终 Markdown 答案，不再出现 `missing field tool_call_id` 的 422。
- 使用假 Chat Completions 服务验证工具回填顺序：第二次请求为 `system/assistant/user/user`，没有非法的 `role: tool`。
- 启动时不再复用固定 id `local-conversation`，改为每次生成新的会话 id；历史记录仍显示在侧栏，但默认不选中任何旧会话，主区域显示“新对话”空状态。
- Provider 401/403 映射为 `AI_AUTH_ERROR`，对话界面显示“API Key 无效或没有访问权限，请在账户设置中检查。”，不再统一显示“服务暂时不可用”；429/5xx/连接失败也分别映射。
- 账户弹窗主色改为深灰色；已保存的 API Key 不回显，但显示“已保存，留空保持不变”的提示。
- 移除账户设置中的“启用记忆”入口及请求里的记忆上下文注入。
- 打包体积：Mac 目录包 639 MB → 344 MB（完整 JDK 330 MB → jlink 最小 JRE 61 MB，app.asar 28 MB → 2.7 MB）；Mac zip 163 MB；Windows 目录包 451 MB、便携 zip 178 MB、NSIS 安装包 128 MB。
- Windows x64 已使用 Adoptium JRE 17 x64 准备 `runtime/jre/win-x64`，`yunhe.exe` 为 PE32+ x86-64，安装包已生成；Windows 实机启动仍需在 Windows 环境完成最终验证。
- `ToolCallParser` 增加对模型漏写 `tool_calls` 数组结尾 `]` 的容错恢复，并新增回归测试；无法恢复时返回 `INVALID_TOOL_ARGUMENTS`，不再把原始 JSON 当答案显示。
- 已重新生成 `release/mac-arm64/yunhe.app`，后端 JAR 与 `runtime/backend/qa-backend-0.1.0.jar` 哈希一致。
- 已知环境限制：当前 DSH 外层 sandbox 下 Electron 的 OS sandbox 无法启动，`verify:ipc` 需在普通终端运行；应用本体已通过 `open` 启动并健康检查通过。

## 已通过

- Spring Boot 全量 Maven 测试
- React/Vitest 组件测试：空状态、发送问题和 Markdown 回答
- Electron IPC：版本、问答、历史读写和错误分支
- Electron 生产构建
- Electron Builder macOS arm64 目录包生成
- Electron Builder Windows x64 目录包生成，产物为 PE32+ x86-64 可执行文件
- 目录包实际启动，修复并验证生产入口路径

## 命令

```bash
npm run verify:all
npm --workspace apps/desktop run test
npm --workspace apps/desktop run package:dir
```

正式发布前仍需 Apple Developer 签名、公证和真实可用的 DeepSeek 模型名确认。
Windows `.exe` 已在 macOS 上完成构建和文件格式验证；由于当前不是 Windows 环境，需在 Windows 实机完成最终启动验证。

Windows 迁移入口：`scripts/build-windows.ps1` 负责安装依赖、构建桌面包和后端 JAR；`scripts/run-windows.ps1` 负责读取本地环境变量并打开桌面应用，后端由 Electron 自动启动。

## 阶段 11 一键启动验收

- Electron 主进程自动管理 Spring Boot 子进程；开发环境启动 Maven，正式版启动随包 JAR 和平台 Java Runtime。
- 启动最多等待 30 秒，每 500ms 轮询 `/api/v1/health`；8080 被占用、启动超时和子进程提前退出均进入可重试状态。
- UI 在后端未就绪时禁用提问，显示启动状态；应用退出时发送优雅终止信号，并以单实例锁阻止重复启动。
- 后端日志写入用户数据目录并按 2 MiB 轮转，过滤 API Key、问题正文和答案正文。
- `node --test scripts/verify-runtime-assets.test.cjs`：3 个资源失败场景通过。
- `npm run verify:startup:mac`：通过，状态序列为 `ready → stopped`。
- `npm run verify:startup:windows`：通过，完成 Windows x64 生命周期模拟验证；Windows 实机启动仍需在 Windows 主机验收。
- 当前 Mac arm64 目录包已包含 `Resources/backend/qa-backend-0.1.0.jar` 和 `Resources/jre/mac-arm64`，包体积约 629 MiB（含 Java Runtime）。
- 正式发布仍需 Apple Developer 签名/公证、Windows 代码签名和杀毒误报检查。
