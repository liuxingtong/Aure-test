package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class ToolCallParser {
    private static final String TOOL_CALLS = "\"tool_calls\"";
    private static final String NAME = "\"name\"";
    private static final String ARGUMENTS = "\"arguments\"";
    private final ObjectMapper mapper;

    public ToolCallParser(ObjectMapper mapper) { this.mapper = mapper; }

    /**
     * 解析模型输出：
     * - 提取 tool_calls 列表
     * - 同时保留 JSON 之外的自然语言文字，作为 {@code naturalLanguage} 返回，
     *   这样 "我先算一下" 之类的话不会在工具循环里被默默丢掉。
     * - 模型偶尔会漏掉 tool_calls 数组的最后一个 ]（尤其参数里带大量换行/表格时），
     *   这里做一个针对 arguments 对象的容错提取；无法恢复时抛 ToolException，
     *   避免把整段 JSON 当成答案显示给用户。
     */
    public ParseResult parse(String content) {
        var raw = content == null ? "" : content.trim();
        if (raw.isEmpty()) return new ParseResult(List.of(), "");
        var start = raw.indexOf('{');
        var end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            if (raw.contains(TOOL_CALLS)) return recoverOrThrow(raw, "");
            return new ParseResult(List.of(), raw);
        }

        var naturalLanguage = (raw.substring(0, start) + raw.substring(end + 1)).trim();
        var json = raw.substring(start, end + 1);
        try {
            var root = mapper.readTree(json);
            var callsNode = root.path("tool_calls");
            if (!callsNode.isArray()) {
                if (raw.contains(TOOL_CALLS)) return recoverOrThrow(raw, naturalLanguage);
                return new ParseResult(List.of(), naturalLanguage);
            }
            var result = new java.util.ArrayList<ToolCall>();
            for (var call : callsNode) {
                var name = call.path("name").asText("").trim();
                if (name.isBlank() || !call.has("arguments")) throw new IllegalArgumentException("工具调用格式无效");
                result.add(new ToolCall(name, call.get("arguments")));
            }
            return new ParseResult(List.copyOf(result), naturalLanguage);
        } catch (ToolException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            if (raw.contains(TOOL_CALLS)) return recoverOrThrow(raw, naturalLanguage);
            throw exception;
        } catch (Exception exception) {
            if (raw.contains(TOOL_CALLS)) return recoverOrThrow(raw, naturalLanguage);
            // JSON 不合法时，整段视为自然语言
            return new ParseResult(List.of(), raw);
        }
    }

    private ParseResult recoverOrThrow(String raw, String naturalLanguage) {
        var calls = recoverCalls(raw);
        if (calls == null || calls.isEmpty()) {
            throw new ToolException("INVALID_TOOL_ARGUMENTS", "模型返回的工具调用格式无效，请重试。", false);
        }
        return new ParseResult(List.copyOf(calls), naturalLanguage);
    }

    /**
     * 从容错角度提取 name/arguments：不要求外层 tool_calls 数组闭合，只要 arguments
     * 对象本身是平衡的。用于模型漏写 ] 或 } 的情况。
     */
    private List<ToolCall> recoverCalls(String raw) {
        var calls = new java.util.ArrayList<ToolCall>();
        var searchFrom = raw.indexOf(TOOL_CALLS);
        if (searchFrom < 0) return null;
        var nameIndex = raw.indexOf(NAME, searchFrom);
        while (nameIndex >= 0) {
            try {
                var nameColon = raw.indexOf(':', nameIndex + NAME.length());
                if (nameColon < 0) return null;
                var nameStart = raw.indexOf('"', nameColon + 1);
                if (nameStart < 0) return null;
                var nameEnd = findStringEnd(raw, nameStart + 1);
                if (nameEnd < 0) return null;
                var name = mapper.readValue(raw.substring(nameStart, nameEnd + 1), String.class).trim();
                if (name.isBlank()) return null;

                var argumentsIndex = raw.indexOf(ARGUMENTS, nameEnd + 1);
                if (argumentsIndex < 0) return null;
                var argumentsColon = raw.indexOf(':', argumentsIndex + ARGUMENTS.length());
                if (argumentsColon < 0) return null;
                var objectStart = raw.indexOf('{', argumentsColon + 1);
                if (objectStart < 0) return null;
                var objectEnd = findMatchingBrace(raw, objectStart);
                if (objectEnd < 0) return null;

                JsonNode arguments = mapper.readTree(raw.substring(objectStart, objectEnd + 1));
                calls.add(new ToolCall(name, arguments));
                nameIndex = raw.indexOf(NAME, objectEnd + 1);
            } catch (Exception exception) {
                return null;
            }
        }
        return calls;
    }

    private static int findStringEnd(String text, int start) {
        var escaped = false;
        for (var i = start; i < text.length(); i++) {
            var c = text.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static int findMatchingBrace(String text, int start) {
        var depth = 0;
        var inString = false;
        var escaped = false;
        for (var i = start; i < text.length(); i++) {
            var c = text.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    public record ParseResult(List<ToolCall> calls, String naturalLanguage) {}
}
