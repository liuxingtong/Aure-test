package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReadDocumentTool implements AiTool {
    @Override public String name() { return "read_document"; }
    @Override public String description() { return "读取用户通过 Electron 文件选择器授权的 md/txt/json 文档（支持多文件）。" +
            "可指定 filename（不含扩展名）选择具体文件；省略则返回所有文档的拼接摘要。"; }

    @Override
    public String execute(JsonNode arguments) {
        if (arguments.has("path")) throw new ToolException("INVALID_TOOL_ARGUMENTS", "path 不允许由模型提供", false);
        var documentsNode = arguments.path("documents");
        if (!documentsNode.isArray() || documentsNode.isEmpty()) {
            return "DOCUMENT_SELECTION_REQUIRED：请在 Electron 文件选择器中选择文档";
        }
        var documents = new ArrayList<JsonNode>();
        documentsNode.forEach(documents::add);
        var requestedName = arguments.has("filename") ? arguments.path("filename").asText("").trim() : "";

        if (!requestedName.isEmpty()) {
            for (var doc : documents) {
                var name = doc.path("name").asText("");
                if (name.equalsIgnoreCase(requestedName) || name.equalsIgnoreCase(requestedName + "." + doc.path("extension").asText(""))) {
                    return renderOne(doc);
                }
            }
            throw new ToolException("DOCUMENT_NOT_FOUND", "没有找到名为 " + requestedName + " 的附件", false);
        }
        // 不指定 filename：返回所有文档的摘要
        var sb = new StringBuilder("已选文档（" + documents.size() + " 个）：\n");
        for (int i = 0; i < documents.size(); i++) {
            var doc = documents.get(i);
            sb.append("\n[").append(i + 1).append("] ").append(doc.path("name").asText("文档"));
            var ext = doc.path("extension").asText("");
            if (!ext.isEmpty()) sb.append(".").append(ext);
            var content = doc.path("content");
            if (content.isTextual()) {
                var snippet = content.asText();
                if (snippet.length() > 200) snippet = snippet.substring(0, 200) + "…";
                sb.append("\n    预览：").append(snippet);
            }
        }
        sb.append("\n\n如需查看完整内容，请重新调用本工具并通过 filename 指定要读取的文档。");
        return sb.toString();
    }

    private String renderOne(JsonNode document) {
        var extension = document.path("extension").asText("").toLowerCase().replaceFirst("^\\.", "");
        if (!extension.matches("md|txt|json")) throw new ToolException("INVALID_TOOL_ARGUMENTS", "不支持的文件扩展名：" + extension, false);
        var name = document.path("name").asText("文档");
        var content = document.path("content");
        if (!content.isTextual()) throw new ToolException("DOCUMENT_INVALID_ENCODING", "文档内容无效", false);
        return "文档：" + name + "\n\n" + content.asText();
    }
}
