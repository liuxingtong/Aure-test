package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class ToolRegistry {
    private final Map<String, AiTool> tools = new LinkedHashMap<>();

    @Autowired
    public ToolRegistry(CalculateTool calculate, SearchLatestNewsTool news, PlanTool plan, MarkdownDocumentTool markdown, ReadDocumentTool document) {
        tools.put(calculate.name(), calculate);
        tools.put(news.name(), news);
        if (plan != null) tools.put(plan.name(), plan);
        if (markdown != null) tools.put(markdown.name(), markdown);
        if (document != null) tools.put(document.name(), document);
    }

    public List<String> names() { return List.copyOf(tools.keySet()); }

    public String execute(String name, JsonNode arguments) {
        var tool = tools.get(name);
        if (tool == null) throw new ToolException("UNKNOWN_TOOL", "未知工具：" + name, false);
        try {
            return tool.execute(arguments);
        } catch (ToolException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ToolException("INVALID_TOOL_ARGUMENTS", exception.getMessage(), false);
        } catch (RuntimeException exception) {
            throw new ToolException("TOOL_EXECUTION_FAILED", "工具执行失败", false);
        }
    }
}
