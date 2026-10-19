# yunhe

一个 Electron + React + Spring Boot 的本地 AI 问答桌面应用，界面采用 OpenAI/ChatGPT 风格，支持 Markdown、会话历史、AI Adapter 和 Function Calling。

## 开发环境

- Node.js 22+
- Java 17+
- Maven 3.9+
- macOS arm64（当前已验证）

## 配置 DeepSeek

复制 `.env.local.example` 为 `.env.local`，填写 API Key。`.env.local` 已被 Git 忽略，不应提交。

```bash
./scripts/start-dev.sh
```

当前配置使用 `https://api.deepseek.com` 和 `deepseek-flash`，对应 DeepSeek-V4.1-Flash；如账户模型列表不同，请以 `GET /models` 返回的 `id` 为准。

## 验证与打包

```bash
npm run verify:all
npm run verify:startup:mac
npm run verify:startup:windows
npm --workspace apps/desktop run test
npm --workspace apps/desktop run package:dir
```

正式版会把 Spring Boot JAR 和平台 Java Runtime 放进应用资源，用户双击应用后由 Electron 自动启动后端并等待健康检查。macOS arm64 打包前设置 `QA_JRE_SOURCE` 或 `JAVA_HOME` 指向 Java 17 Runtime，然后执行 `./scripts/prepare-backend-runtime.sh`；资源校验命令为 `node scripts/verify-runtime-assets.cjs --platform darwin --arch arm64`。`package:dir` 生成未签名的 macOS arm64 目录包到 `apps/desktop/release/mac-arm64`。正式发布还需要配置 Apple Developer 签名和公证。

Windows 迁移：将源码包复制到 Windows 后，在 PowerShell 设置 `QA_JRE_SOURCE` 或 `JAVA_HOME` 指向 Java 17 Runtime，执行 `.\scripts\build-windows.ps1`。构建脚本会准备后端和 Runtime；直接双击 `apps/desktop/release/win-unpacked/问答桌面应用.exe` 即可一键启动全部服务。Windows 正式发布还需要代码签名，并在目标机器检查杀毒软件误报。

## 目录结构

- `apps/desktop`：Electron 主进程、Preload、React UI
- `apps/backend`：Spring Boot REST API、AI Adapter、工具调用和监控
- `packages/shared-types`：前后端共享类型
- `docs`：架构设计和验收报告
