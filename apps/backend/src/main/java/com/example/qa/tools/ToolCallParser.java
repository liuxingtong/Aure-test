package com.example.qa.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class ToolCallParser {
    private final ObjectMapper mapper;

    public ToolCallParser(ObjectMapper mapper) { this.mapper = mapper; }

    public List<ToolCall> parse(String content) {
        try {
            var root = mapper.readTree(content);
            var calls = root.path("tool_calls");
            if (!calls.isArray()) return List.of();
            var result = new java.util.ArrayList<ToolCall>();
            for (var call : calls) {
                var name = call.path("name").asText("").trim();
                if (name.isBlank() || !call.has("arguments")) throw new IllegalArgumentException("工具调用格式无效");
                result.add(new ToolCall(name, call.get("arguments")));
            }
            return List.copyOf(result);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            return List.of();
        }
    }
}
