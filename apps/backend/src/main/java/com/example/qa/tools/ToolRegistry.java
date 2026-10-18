package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {
    private final Map<String, AiTool> tools = new LinkedHashMap<>();

    public ToolRegistry(CalculateTool calculate, SearchLatestNewsTool news) {
        tools.put(calculate.name(), calculate);
        tools.put(news.name(), news);
    }

    public List<String> names() { return List.copyOf(tools.keySet()); }

    public String execute(String name, JsonNode arguments) {
        var tool = tools.get(name);
        if (tool == null) throw new IllegalArgumentException("未知工具：" + name);
        return tool.execute(arguments);
    }
}
