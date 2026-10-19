package com.example.qa.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class ToolCallParser {
    private final ObjectMapper mapper;

    public ToolCallParser(ObjectMapper mapper) { this.mapper = mapper; }

    /**
     * 解析模型输出：
     * - 提取 tool_calls 列表
     * - 同时保留 JSON 之外的自然语言文字，作为 {@code naturalLanguage} 返回，
     *   这样 "我先算一下" 之类的话不会在工具循环里被默默丢掉。
     */
    public ParseResult parse(String content) {
        var raw = content == null ? "" : content.trim();
        if (raw.isEmpty()) return new ParseResult(List.of(), "");
        var start = raw.indexOf('{');
        var end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return new ParseResult(List.of(), raw);

        var naturalLanguage = (raw.substring(0, start) + raw.substring(end + 1)).trim();
        var json = raw.substring(start, end + 1);
        try {
            var root = mapper.readTree(json);
            var callsNode = root.path("tool_calls");
            if (!callsNode.isArray()) return new ParseResult(List.of(), naturalLanguage);
            var result = new java.util.ArrayList<ToolCall>();
            for (var call : callsNode) {
                var name = call.path("name").asText("").trim();
                if (name.isBlank() || !call.has("arguments")) throw new IllegalArgumentException("工具调用格式无效");
                result.add(new ToolCall(name, call.get("arguments")));
            }
            return new ParseResult(List.copyOf(result), naturalLanguage);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            // JSON 不合法时，整段视为自然语言
            return new ParseResult(List.of(), raw);
        }
    }

    public record ParseResult(List<ToolCall> calls, String naturalLanguage) {}
}
