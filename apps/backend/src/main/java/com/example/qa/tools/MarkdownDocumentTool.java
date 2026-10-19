package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class MarkdownDocumentTool implements AiTool {
    @Override public String name() { return "generate_markdown"; }
    @Override public String description() { return "根据标题和内容生成规范 Markdown 文档，不写入文件"; }
    @Override public String execute(JsonNode arguments) {
        var title = arguments.path("title").asText("").trim();
        var content = arguments.path("content").asText("").trim();
        if (title.isBlank()) throw new IllegalArgumentException("title 不能为空");
        if (content.isBlank()) throw new IllegalArgumentException("content 不能为空");
        return "# " + title + "\n\n" + content + "\n";
    }
}
