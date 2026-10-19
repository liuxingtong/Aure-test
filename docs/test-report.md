# 阶段 10 验收报告

日期：2026-10-18

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
