package com.example.qa.tools;

import com.example.qa.ai.AiClient;
import com.example.qa.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ToolCallingService {
    static final int MAX_CALLS = 3;
    private final AiClient aiClient;
    private final ToolRegistry registry;
    private final ToolCallParser parser;

    public ToolCallingService(AiClient aiClient, ToolRegistry registry, ObjectMapper mapper) {
        this.aiClient = aiClient;
        this.registry = registry;
        this.parser = new ToolCallParser(mapper);
    }

    public String answer(List<ChatMessage> history, String question) {
        if (isSearchRequest(question)) {
            return registry.execute("search_latest_news", new ObjectMapper().createObjectNode().put("query", question.trim()));
        }
        var context = new ArrayList<>(history == null ? List.of() : history);
        var answer = aiClient.chat(context, question);
        var callsUsed = 0;
        while (true) {
            var calls = parser.parse(answer);
            if (calls.isEmpty()) return answer;
            if (callsUsed + calls.size() > MAX_CALLS) throw new IllegalStateException("工具调用次数超过上限");
            var toolResults = new ArrayList<String>();
            for (var call : calls) {
                var result = registry.execute(call.name(), call.arguments());
                context.add(new ChatMessage("tool-" + callsUsed, "tool", call.name() + " 结果：" + result, "now"));
                toolResults.add(result);
                callsUsed++;
            }
            return String.join("\n\n", toolResults);
        }
    }

    private boolean isSearchRequest(String question) {
        if (question == null || question.isBlank()) return false;
        var normalized = question.toLowerCase();
        return normalized.contains("搜索") || normalized.contains("联网") || normalized.contains("新闻")
                || normalized.contains("查一下") || normalized.contains("查找") || normalized.contains("latest news")
                || normalized.contains("web search") || normalized.contains("browse");
    }
}
