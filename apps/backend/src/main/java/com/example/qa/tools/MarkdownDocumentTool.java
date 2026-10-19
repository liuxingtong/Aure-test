package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MarkdownDocumentTool implements AiTool {
    @Override public String name() { return "generate_markdown"; }
    @Override public String description() { return "根据标题和内容生成规范 Markdown 文档，不写入文件"; }
    @Override public String execute(JsonNode arguments) {
        var title = arguments.path("title").asText("").trim();
        if (title.isBlank()) throw new IllegalArgumentException("title 不能为空");
        var sections = new ArrayList<String>();
        var content = arguments.path("content");
        if (content.isTextual() && !content.asText().trim().isBlank()) sections.add(content.asText().trim());
        else if (arguments.has("content")) throw new IllegalArgumentException("content 必须是非空文本");

        var list = arguments.path("list");
        if (list.isArray()) {
            var items = new ArrayList<String>();
            list.forEach(item -> {
                if (!item.isTextual() || item.asText().trim().isBlank()) throw new IllegalArgumentException("list 必须是字符串数组");
                items.add("- " + item.asText().trim());
            });
            if (!items.isEmpty()) sections.add(String.join("\n", items));
        } else if (arguments.has("list")) throw new IllegalArgumentException("list 必须是字符串数组");

        if (arguments.has("table")) sections.add(renderTable(arguments.path("table")));
        if (arguments.has("code")) sections.add(renderCode(arguments.path("code")));
        if (sections.isEmpty()) throw new IllegalArgumentException("content、list、table 或 code 至少提供一项");
        return "# " + title + "\n\n" + String.join("\n\n", sections) + "\n";
    }

    private static String renderTable(JsonNode table) {
        if (!table.isObject() || !table.path("headers").isArray() || !table.path("rows").isArray() || table.path("headers").isEmpty()) {
            throw new IllegalArgumentException("table 必须包含 headers 和 rows 数组");
        }
        var headers = strings(table.path("headers"), "table.headers");
        var lines = new ArrayList<String>();
        lines.add(row(headers));
        lines.add(row(headers.stream().map(value -> "---").toList()));
        for (var rawRow : table.path("rows")) {
            if (!rawRow.isArray()) throw new IllegalArgumentException("table.rows 必须是数组");
            var cells = new ArrayList<String>();
            for (var i = 0; i < headers.size(); i++) {
                var cell = i < rawRow.size() ? rawRow.get(i) : null;
                if (cell != null && !cell.isValueNode()) throw new IllegalArgumentException("table.rows 必须包含文本单元格");
                cells.add(cell == null || cell.isNull() ? "" : escape(cell.asText()));
            }
            lines.add(row(cells));
        }
        return String.join("\n", lines);
    }

    private static String renderCode(JsonNode code) {
        if (!code.isObject() || !code.path("content").isTextual() || code.path("content").asText().isBlank()) {
            throw new IllegalArgumentException("code 必须包含非空 content");
        }
        var language = code.path("language").asText("").trim();
        return "```" + language + "\n" + code.path("content").asText() + "\n```";
    }

    private static List<String> strings(JsonNode node, String field) {
        var values = new ArrayList<String>();
        for (var item : node) {
            if (!item.isTextual() || item.asText().trim().isBlank()) throw new IllegalArgumentException(field + " 必须是非空字符串数组");
            values.add(escape(item.asText().trim()));
        }
        return values;
    }

    private static String row(List<String> cells) { return "| " + String.join(" | ", cells) + " |"; }
    private static String escape(String value) { return value.replace("|", "\\|").replace("\n", " "); }
}
