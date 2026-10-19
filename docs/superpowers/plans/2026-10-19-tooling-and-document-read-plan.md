# 工具增强与安全文档读取实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 完善现有工具链，增加稳定的计划与 Markdown 生成、安全的 Electron 授权文档读取，以及可理解的工具错误分类。

**Architecture:** 后端继续承载工具注册、参数校验和 Function Calling；Electron 主进程承载系统文件选择、文件安全校验与一次性授权上下文。共享错误码通过后端响应和桌面 IPC 原样透传，渲染层只消费用户可读消息。

**Tech Stack:** Java 17、Spring Boot 3、Jackson、JUnit 5/AssertJ、Electron 35、TypeScript、Vitest、React、npm workspaces。

**Spec:** `docs/superpowers/specs/2026-10-19-tooling-and-document-read-design.md`

## Global Constraints

- 严格按 `TODO.md` 顺序推进，每个阶段单独提交 Git。
- 每个阶段先补失败测试，再写生产代码。
- 不改变 Provider、设置保存、安全存储、后端自动重启、记忆、归档历史和一键启动链路。
- 文档只允许 md/txt/json、最大 1 MiB、UTF-8，不允许模型任意扫描目录或执行内容。
- 完成后运行后端测试、前端测试、TypeScript/生产构建和 macOS arm64 打包验证。

## Review Focus

- 缺省/空数组/混合参数时工具输出仍是稳定 Markdown，而不是空标题或多余分隔线。
- 表格单元格包含换行或 Markdown 符号时不会破坏表格结构。
- 文件选择取消、文件不存在、超过 1 MiB、非 UTF-8 都返回独立可理解错误。
- 未知工具和非法参数不会被包装成“服务暂时不可用”。
- 现有 Provider 和会话历史请求不携带或记录 API Key、文件正文之外的敏感信息。

### Task 1: 增强 `create_plan`

**Files:**
- Modify: `apps/backend/src/main/java/com/example/qa/tools/PlanTool.java`
- Test: `apps/backend/src/test/java/com/example/qa/tools/PlanToolTest.java`

**Interfaces:**
- Produces: `PlanTool.execute(JsonNode)` 接受 `goal`, `constraints`, `technology`, `phases`, `acceptance`，返回固定章节与单尾换行的 Markdown。

- [ ] **Step 1: Write the failing tests**
  - 默认阶段/验收标准；自定义字符串数组；缺少 `goal`；空数组回退默认值；输出末尾恰好一个换行。
- [ ] **Step 2: Run tests to verify they fail**
  - `mvn -q -f apps/backend/pom.xml -Dtest=PlanToolTest test`
  - Expected: 新断言因参数未处理或格式不符而失败。
- [ ] **Step 3: Implement minimal `PlanTool` behavior**
  - 统一解析字符串/数组；固定四个章节、阶段编号和验收复选框；清理首尾空白并规范单尾换行。
- [ ] **Step 4: Run focused and backend tests**
  - `mvn -q -f apps/backend/pom.xml -Dtest=PlanToolTest test`
  - `mvn -q -f apps/backend/pom.xml test`
- [ ] **Step 5: Commit**
  - `git add apps/backend/src/main/java/com/example/qa/tools/PlanTool.java apps/backend/src/test/java/com/example/qa/tools/PlanToolTest.java && git commit -m "feat: enhance create plan tool"`

### Task 2: 增强 `generate_markdown`

**Files:**
- Modify: `apps/backend/src/main/java/com/example/qa/tools/MarkdownDocumentTool.java`
- Modify: `apps/backend/src/test/java/com/example/qa/tools/ToolRegistryTest.java`
- Create: `apps/backend/src/test/java/com/example/qa/tools/MarkdownDocumentToolTest.java`

**Interfaces:**
- Produces: `MarkdownDocumentTool.execute(JsonNode)` 支持 `title`, `content`, `list`, `table.headers`, `table.rows`, `code.language`, `code.content`，按固定顺序输出 Markdown。

- [ ] **Step 1: Write the failing tests**
  - 标题+正文；列表；表格；代码块；仅标题/空参数失败；错误数组或表格形状失败；组合内容顺序和单尾换行。
- [ ] **Step 2: Run tests to verify they fail**
  - `mvn -q -f apps/backend/pom.xml -Dtest=MarkdownDocumentToolTest,ToolRegistryTest test`
- [ ] **Step 3: Implement minimal renderer and validation**
  - 只渲染已提供的区块；列表每项一行；表格补齐短行空单元格并转义 `|`；代码围栏使用语言字段；不写入文件。
- [ ] **Step 4: Run focused and backend tests**
  - `mvn -q -f apps/backend/pom.xml -Dtest=MarkdownDocumentToolTest,ToolRegistryTest test`
  - `mvn -q -f apps/backend/pom.xml test`
- [ ] **Step 5: Commit**
  - `git add apps/backend/src/main/java/com/example/qa/tools/MarkdownDocumentTool.java apps/backend/src/test/java/com/example/qa/tools/MarkdownDocumentToolTest.java apps/backend/src/test/java/com/example/qa/tools/ToolRegistryTest.java && git commit -m "feat: expand markdown generation tool"`

### Task 3: 新增安全 `read_document` 选择链路

**Files:**
- Modify: `apps/backend/src/main/java/com/example/qa/tools/ToolRegistry.java`
- Create: `apps/backend/src/main/java/com/example/qa/tools/ReadDocumentTool.java`
- Modify: `apps/backend/src/main/java/com/example/qa/model/QuestionRequest.java`
- Modify: `apps/backend/src/main/java/com/example/qa/tools/ToolCallingService.java`
- Modify: `apps/desktop/electron/ipc.ts`
- Modify: `apps/desktop/preload/index.ts`
- Modify: `apps/desktop/src/electron.d.ts`
- Modify: `apps/desktop/electron/backend-client.ts`
- Test: `apps/backend/src/test/java/com/example/qa/tools/ReadDocumentToolTest.java`
- Test: `apps/desktop/electron/document-picker.test.ts`

**Interfaces:**
- Produces: `ReadDocumentTool` 返回 `DOCUMENT_SELECTION_REQUIRED` 或消费一次性 `{name, extension, content}` 授权上下文；Electron 暴露 `chooseDocument()`，只返回允许扩展名的 UTF-8 内容和元数据。

- [ ] **Step 1: Write failing backend and Electron tests**
  - 后端无授权上下文时要求选择；有授权内容时返回内容；拒绝路径参数；Electron 取消选择；扩展名过滤；超过 1 MiB；无效 UTF-8。
- [ ] **Step 2: Run tests to verify they fail**
  - `mvn -q -f apps/backend/pom.xml -Dtest=ReadDocumentToolTest test`
  - `npm --workspace apps/desktop test -- document-picker.test.ts`
- [ ] **Step 3: Implement backend contract**
  - 注册工具；在问题请求中增加可选一次性文档上下文；工具只读取上下文，不访问任意路径。
- [ ] **Step 4: Implement Electron picker and preload API**
  - 主进程使用 `dialog.showOpenDialog`，过滤 md/txt/json；读取 Buffer 并严格 UTF-8 解码、大小校验；取消返回 `null`。
- [ ] **Step 5: Run focused and full tests**
  - `mvn -q -f apps/backend/pom.xml test`
  - `npm --workspace apps/desktop test`
  - `npm run typecheck`
- [ ] **Step 6: Commit**
  - `git add apps/backend apps/desktop/electron apps/desktop/preload apps/desktop/src/electron.d.ts && git commit -m "feat: add safe document reading flow"`

### Task 4: 完善工具错误分类与透传

**Files:**
- Modify: `apps/backend/src/main/java/com/example/qa/tools/ToolRegistry.java`
- Modify: `apps/backend/src/main/java/com/example/qa/tools/ToolCallingService.java`
- Modify: `apps/backend/src/main/java/com/example/qa/exception/GlobalExceptionHandler.java`
- Modify: `apps/backend/src/main/java/com/example/qa/model/QuestionResponse.java`
- Modify: `apps/desktop/electron/backend-client.ts`
- Modify: `apps/desktop/electron/ipc.ts`
- Test: `apps/backend/src/test/java/com/example/qa/controller/QuestionControllerTest.java`
- Test: `apps/desktop/electron/backend-client.test.ts`

**Interfaces:**
- Produces: 稳定错误码 `UNKNOWN_TOOL`, `INVALID_TOOL_ARGUMENTS`, `DOCUMENT_SELECTION_REQUIRED`, `DOCUMENT_NOT_FOUND`, `DOCUMENT_TOO_LARGE`, `DOCUMENT_INVALID_ENCODING`, `TOOL_EXECUTION_FAILED` 及用户可读消息；Electron 保留后端错误码和 retryable 属性。

- [ ] **Step 1: Write failing tests**
  - Controller/Service 覆盖未知工具、非法参数、五类文档错误；桌面客户端覆盖错误码透传，不再统一改写为 `BACKEND_UNAVAILABLE`。
- [ ] **Step 2: Run tests to verify they fail**
  - `mvn -q -f apps/backend/pom.xml -Dtest=QuestionControllerTest test`
  - `npm --workspace apps/desktop test -- backend-client.test.ts`
- [ ] **Step 3: Implement typed error mapping**
  - 引入工具异常类型/错误码；全局异常处理按错误码返回；HTTP 客户端只在网络/未知响应时使用通用后端错误。
- [ ] **Step 4: Run all required verification**
  - `mvn -q -f apps/backend/pom.xml test`
  - `npm --workspace apps/desktop test`
  - `npm run typecheck`
  - `npm run build:desktop`
  - `npm run verify:ipc`
  - `npm run package:dir --workspace apps/desktop`
- [ ] **Step 5: Commit**
  - `git add apps/backend apps/desktop/electron && git commit -m "feat: classify and preserve tool errors"`

## Final Verification

- [ ] `mvn -q -f apps/backend/pom.xml test`
- [ ] `npm --workspace apps/desktop test`
- [ ] `npm run typecheck`
- [ ] `npm run build:desktop`
- [ ] `npm run verify:ipc`
- [ ] `npm run package:dir --workspace apps/desktop`
- [ ] 确认 `apps/desktop/release/mac-arm64/yunhe.app` 存在并记录 Git 状态与四个阶段提交。

