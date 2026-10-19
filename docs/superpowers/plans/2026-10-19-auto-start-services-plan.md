# 桌面应用一键启动全部服务 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Mac arm64 和 Windows x64 正式版在用户双击桌面应用后自动启动 Spring Boot、等待健康检查通过，并在退出时清理后端进程。

**Architecture:** Electron 主进程新增 `BackendProcessManager`，统一管理开发环境 Maven 启动和正式版 JAR+平台 Java Runtime 启动。主进程通过 IPC 把启动状态同步到 React，打包配置把后端 JAR 和匹配的 Runtime 放入 resources，并由脚本在打包前验证资源、架构和退出清理。

**Tech Stack:** Electron 35、React 19、TypeScript、Vitest、Spring Boot 3.4、Java 17、Maven、electron-builder、macOS arm64、Windows x64。

**Spec:** `docs/superpowers/specs/2026-10-19-auto-start-services-design.md`

## Global Constraints

- 正式版不要求用户预装 Java；Mac arm64 和 Windows x64 必须各自携带匹配的 Java Runtime。
- 后端默认绑定 `127.0.0.1:8080`；端口被外部进程占用时必须明确失败，不静默改端口。
- 健康检查最多等待 30 秒，每 500ms 请求一次 `/api/v1/health`。
- 退出时优雅关闭后端，最多等待 3 秒，只终止本次应用启动的进程。
- Renderer 不接触 API Key、Node.js、child process 或文件系统。
- 日志不得包含 API Key、问题正文或答案正文。
- 每个独立任务完成后运行对应测试并单独提交 Git。

## Review Focus

- 后端 JAR 启动后立刻退出：测试应展示退出原因，并且 UI 进入可重试状态。
- 8080 被其他进程占用：测试应阻止启动并显示明确端口错误，不改变端口。
- 健康检查在 30 秒内未成功：测试应停止等待并允许重新启动。
- 应用退出或后端异常退出：测试应确认不会遗留由本应用创建的 Java 进程。
- 打包资源缺失或 Runtime 架构错误：构建前检查应失败并指出具体路径/架构。

---

### Task 1: 后端进程管理器

**Files:**
- Create: `apps/desktop/electron/backend-process-manager.ts`
- Create: `apps/desktop/electron/backend-process-manager.test.ts`
- Modify: `apps/desktop/electron/backend-client.ts`

**Interfaces:**
- Produces `BackendStatus = { state: 'starting' | 'ready' | 'error' | 'stopped'; message?: string }`。
- Produces `BackendProcessManager.start(): Promise<BackendStatus>`、`stop(): Promise<void>`、`getStatus(): BackendStatus`。
- `BackendProcessManager` 接收 `mode`, `platform`, `resourcesPath`, `userDataPath`, `spawn`, `fetch`, `clock` 等可注入依赖，便于测试。

- [ ] **Step 1: Write failing tests** for successful health polling, child-process early exit, timeout at 30 seconds, port conflict, and graceful stop.
- [ ] **Step 2: Run `npm --workspace apps/desktop run test -- backend-process-manager.test.ts`** and verify the new tests fail for missing manager behavior.
- [ ] **Step 3: Implement the manager** with 500ms polling, 30-second deadline, fixed `127.0.0.1:8080`, platform-aware command resolution, log file creation, and ownership tracking for spawned processes.
- [ ] **Step 4: Update `backend-client.ts`** so renderer requests are rejected with a typed retryable `BACKEND_STARTING`/`BACKEND_UNAVAILABLE` error when the manager is not ready.
- [ ] **Step 5: Run the focused tests and full desktop tests**; expected result is all manager and existing tests pass.
- [ ] **Step 6: Commit** `feat: manage backend process lifecycle`.

### Task 2: Electron 生命周期和 IPC 状态通道

**Files:**
- Modify: `apps/desktop/electron/main.ts`
- Modify: `apps/desktop/electron/ipc.ts`
- Modify: `apps/desktop/preload/index.ts`
- Modify: `apps/desktop/src/electron.d.ts`
- Create: `apps/desktop/electron/backend-status.test.ts`

**Interfaces:**
- Add IPC `backend:status` request returning `BackendStatus`.
- Add preload API `getBackendStatus(): Promise<BackendStatus>` and `onBackendStatus(listener): () => void`.

- [ ] **Step 1: Write failing IPC tests** for startup status sequence `starting → ready`, error status, and unsubscribe behavior.
- [ ] **Step 2: Run the focused IPC tests** and verify they fail before the new channel exists.
- [ ] **Step 3: Start the manager in `app.whenReady` before `createWindow`**, keep the window available during startup, and trigger manager stop from `before-quit`/`will-quit` without double cleanup.
- [ ] **Step 4: Expose only the typed status methods through contextBridge** and broadcast status changes from main to renderer.
- [ ] **Step 5: Run typecheck, IPC verification, and desktop tests**; expected result is no direct renderer access to Node APIs.
- [ ] **Step 6: Commit** `feat: expose backend readiness over ipc`.

### Task 3: 启动状态 UI 与发送门禁

**Files:**
- Modify: `apps/desktop/src/main.tsx`
- Modify: `apps/desktop/src/styles.css`
- Modify: `apps/desktop/src/main.test.tsx`

**Interfaces:**
- Renderer consumes `getBackendStatus` and `onBackendStatus` from the preload API.
- Renderer renders startup, ready, and error states without adding settings/login/model pages.

- [ ] **Step 1: Add failing component tests** for startup banner, disabled send button while starting, error message with retry action, and automatic recovery to ready.
- [ ] **Step 2: Run the component tests** and verify the new assertions fail.
- [ ] **Step 3: Implement the status banner and send gate** using the existing OpenAI-style layout and current error/retry copy; retry calls the backend manager status action rather than reloading the window.
- [ ] **Step 4: Run all frontend tests and production typecheck/build**; expected result is all existing and startup-state tests pass.
- [ ] **Step 5: Commit** `feat: show backend startup state in desktop ui`.

### Task 4: 后端资源与平台 Runtime 打包

**Files:**
- Create: `scripts/prepare-backend-runtime.sh`
- Create: `scripts/prepare-backend-runtime.ps1`
- Create: `scripts/verify-runtime-assets.cjs`
- Modify: `apps/desktop/package.json`
- Modify: `scripts/build-windows.ps1`
- Modify: `.gitignore`
- Modify: `README.md`

**Interfaces:**
- Standard staging layout: `runtime/backend/qa-backend-0.1.0.jar`, `runtime/jre/mac-arm64/bin/java`, and `runtime/jre/win-x64/bin/java.exe`.
- `verify-runtime-assets.cjs --platform <darwin|win32> --arch <arm64|x64>` exits non-zero for missing files, wrong executable layout, or wrong architecture metadata.

- [ ] **Step 1: Add fixture-based failing tests** for missing JAR, missing Java executable, and unsupported platform/architecture.
- [ ] **Step 2: Run the verifier tests** and confirm they fail before the checker exists.
- [ ] **Step 3: Implement platform preparation scripts** that build the Spring Boot JAR, stage the correct Java Runtime from a configured source, and never commit runtime binaries or API keys.
- [ ] **Step 4: Update electron-builder files** to include staged backend and runtime under `resources/backend` and `resources/jre` while preserving asar behavior for application code.
- [ ] **Step 5: Run verifier and dry-run packaging checks** for Mac and Windows; expected result is clear failure when assets are absent and success when prepared.
- [ ] **Step 6: Commit** `build: bundle backend and platform runtimes`.

### Task 5: 正式版启动、退出与跨平台验证

**Files:**
- Modify: `scripts/verify-all.sh`
- Modify: `scripts/build-windows.ps1`
- Modify: `scripts/run-windows.ps1`
- Modify: `docs/architecture.md`
- Modify: `docs/test-report.md`
- Modify: `README.md`
- Modify: `TODO.md`

**Interfaces:**
- Verification scripts expose `verify:startup:mac` and `verify:startup:windows` checks for health readiness, question request, and process cleanup.

- [ ] **Step 1: Add deterministic startup verification** using a fake backend command and a temporary port so CI does not consume external AI credits.
- [ ] **Step 2: Run the verifier before final packaging** and confirm it catches a deliberately missing runtime or orphaned process.
- [ ] **Step 3: Implement Mac arm64 directory packaging** with staged JAR/Runtime, launch the app, send a demo question, close it, and verify no owned Java process remains.
- [ ] **Step 4: Implement Windows x64 directory packaging** and validate PE architecture, resource layout, PowerShell launch, health readiness, and cleanup on a Windows host or documented equivalent.
- [ ] **Step 5: Record package size, startup latency, test commands, and signing/notarization/antivirus release checks** in the docs.
- [ ] **Step 6: Mark only passed Stage 11 TODO items complete and run the full verification suite.**
- [ ] **Step 7: Commit** `test: verify one-click service startup`.

### Task 6: Final review and delivery

**Files:**
- Modify: `TODO.md`
- Modify: `docs/test-report.md`

- [ ] **Step 1: Run `git diff --check`, frontend tests/build, backend tests, IPC verification, runtime verifier, and both packaging checks.**
- [ ] **Step 2: Review the final diff for API keys, user-content logging, unsigned release claims, and platform-specific path leaks.**
- [ ] **Step 3: Commit** `docs: complete one-click startup delivery`.
- [ ] **Step 4: Deliver the Mac artifact, Windows artifact/source package, verification results, and known signing prerequisites.**
