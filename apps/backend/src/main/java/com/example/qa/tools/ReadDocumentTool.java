package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class ReadDocumentTool implements AiTool {
    @Override public String name() { return "read_document"; }
    @Override public String description() { return "读取用户通过 Electron 文件选择器授权的 md/txt/json 文档"; }

    @Override public String execute(JsonNode arguments) {
        if (arguments.has("path")) throw new ToolException("INVALID_TOOL_ARGUMENTS", "path 不允许由模型提供", false);
        var document = arguments.path("document");
        if (document.isMissingNode() || document.isNull()) return "DOCUMENT_SELECTION_REQUIRED：请在 Electron 文件选择器中选择文档";
        var extension = document.path("extension").asText("").toLowerCase().replaceFirst("^\\.", "");
        if (!extension.matches("md|txt|json")) throw new ToolException("INVALID_TOOL_ARGUMENTS", "不支持的文件扩展名：" + extension, false);
        var name = document.path("name").asText("文档");
        var content = document.path("content");
        if (!content.isTextual()) throw new ToolException("DOCUMENT_INVALID_ENCODING", "文档内容无效", false);
        return "文档：" + name + "\n\n" + content.asText();
    }
}
