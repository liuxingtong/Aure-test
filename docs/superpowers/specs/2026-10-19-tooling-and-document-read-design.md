# 工具增强与安全文档读取设计

## 目标

增强现有 Function Calling 工具链，依次完成：

1. `create_plan` 输出稳定、可执行的 Markdown 计划；
2. `generate_markdown` 支持标题、正文、列表、表格和代码块，并校验空参数；
3. 新增安全的 `read_document` 工具，只支持用户通过 Electron 文件选择器授权的 `md/txt/json` 文件；
4. 将工具错误按用户可理解的类别返回，而不是统一显示“服务暂时不可用”。

## 约束与成功标准

- 严格按 `TODO.md` 的新增工具顺序推进，每个阶段独立提交。
- 每个阶段先增加会失败的测试，再实现最小代码。
- 不改变 Provider Adapter、账户设置、安全存储、后端自动重启、记忆、归档历史和一键启动链路的既有行为。
- 文件读取不允许模型扫描目录、不执行文件内容；仅允许 Electron 系统文件选择器返回的单个文件。
- 支持扩展名：`.md`、`.txt`、`.json`；最大文件大小固定为 1 MiB；编码固定为 UTF-8。
- 生产构建和 macOS arm64 目录打包必须继续通过。

## 架构

后端继续负责工具注册、参数校验和 Function Calling。`read_document` 在后端只表达“需要用户选择文件”的结构化工具结果，不直接接受模型提供的任意路径。Electron 主进程新增受白名单保护的 IPC 选择器：打开系统文件选择器，限制扩展名，读取并校验所选文件，然后把一次性内容作为当前问题请求的授权文档上下文发送给后端。渲染进程只通过 preload 暴露的窄接口访问该能力。

工具错误在后端使用稳定错误码和用户可读消息；Electron HTTP/IPC 层保留错误码并将其展示给 UI。未知工具、参数错误、文件不存在、文件过大、编码错误分别映射到独立错误码，其他未分类异常才使用通用服务错误。

## 工具契约

### `create_plan`

参数：

- `goal`：必填，目标；
- `constraints`：可选，约束；
- `technology`：可选，技术栈；
- `phases`：可选，阶段列表（字符串数组或换行分隔文本）；
- `acceptance`：可选，验收标准（字符串数组或换行分隔文本）。

返回稳定 Markdown，固定包含“目标与边界”“技术栈”“实施阶段”“验收标准”四个标题；缺省阶段和验收标准使用安全默认值；输出末尾恰好一个换行。

### `generate_markdown`

参数：

- `title`：必填；
- `content`：可选正文；
- `list`：可选字符串数组；
- `table`：可选对象，包含 `headers` 和 `rows`；
- `code`：可选对象，包含 `language` 和 `content`。

至少提供 `content`、`list`、`table`、`code` 之一；所有数组/对象形状错误都返回参数错误。输出顺序固定为标题、正文、列表、表格、代码块，并以一个换行结束。

### `read_document`

后端工具参数只包含意图字段，不接受可直接读取的任意路径。若当前请求没有 Electron 授权文档，返回 `DOCUMENT_SELECTION_REQUIRED` 结果，驱动 Electron 打开选择器。Electron 选择并读取后，将 `{name, extension, content}` 作为一次性授权上下文发送；后端仅在该上下文存在时返回文档内容。

## 错误契约

稳定错误码：

- `UNKNOWN_TOOL`：工具名未注册；
- `INVALID_TOOL_ARGUMENTS`：必填参数缺失、空值或形状错误；
- `DOCUMENT_SELECTION_REQUIRED`：需要用户选择文件；
- `DOCUMENT_NOT_FOUND`：授权文件在读取前已不存在；
- `DOCUMENT_TOO_LARGE`：超过 1 MiB；
- `DOCUMENT_INVALID_ENCODING`：无法按 UTF-8 解码；
- `TOOL_EXECUTION_FAILED`：其他工具执行错误。

## 测试与验证

- 后端单元测试覆盖每个工具的成功、空参数和结构错误路径；工具注册表覆盖未知工具错误码。
- Electron 测试覆盖文件选择器过滤器、取消选择、大小限制、UTF-8 解码和 IPC 错误码透传。
- 保留并运行现有后端、前端、TypeScript、生产构建、IPC 联调和 macOS arm64 打包验证。

