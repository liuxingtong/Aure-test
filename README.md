# 问答桌面应用

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

当前配置使用 `https://api.deepseek.com/v1` 和 `4.1flash`；如果服务商返回模型不存在，请替换 `AI_MODEL` 为账户实际可用模型。

## 验证与打包

```bash
npm run verify:all
npm --workspace apps/desktop run test
npm --workspace apps/desktop run package:dir
```

`package:dir` 生成未签名的 macOS arm64 目录包到 `apps/desktop/release/mac-arm64`。正式发布还需要配置 Apple Developer 签名和公证。

## 目录结构

- `apps/desktop`：Electron 主进程、Preload、React UI
- `apps/backend`：Spring Boot REST API、AI Adapter、工具调用和监控
- `packages/shared-types`：前后端共享类型
- `docs`：架构设计和验收报告
