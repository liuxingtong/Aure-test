package com.example.qa.tools;

import com.example.qa.ai.AiClient;
import com.example.qa.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class ToolCallingService {
    static final int MAX_CALLS = 3;
    private final AiClient aiClient;
    private final ToolRegistry registry;
    private final ToolCallParser parser;

    public record AnswerResult(String answer, List<String> toolCalls) {}

    public ToolCallingService(AiClient aiClient, ToolRegistry registry, ObjectMapper mapper) {
        this.aiClient = aiClient;
        this.registry = registry;
        this.parser = new ToolCallParser(mapper);
    }

    public String answer(List<ChatMessage> history, String question) {
        return answerWithTools(history, question, null).answer();
    }

    public AnswerResult answerWithTools(List<ChatMessage> history, String question, String model) {
        return answerWithTools(history, question, model, null);
    }

    public AnswerResult answerWithTools(List<ChatMessage> history, String question, String model, JsonNode document) {
        if (isSearchRequest(question)) {
            var result = registry.execute("search_latest_news", new ObjectMapper().createObjectNode().put("query", question.trim()));
            return new AnswerResult(result, List.of("search_latest_news"));
        }
        var context = new ArrayList<>(history == null ? List.of() : history);
        var answer = aiClient.chat(context, question, model);
        var callsUsed = 0;
        var toolCalls = new ArrayList<String>();
        while (true) {
            var calls = parser.parse(answer);
            if (calls.isEmpty()) return new AnswerResult(answer, List.copyOf(toolCalls));
            if (callsUsed + calls.size() > MAX_CALLS) throw new IllegalStateException("工具调用次数超过上限");
            for (var call : calls) {
                var arguments = call.arguments();
                if ("read_document".equals(call.name()) && document != null && arguments instanceof ObjectNode object) object.set("document", document);
                var result = registry.execute(call.name(), arguments);
                context.add(new ChatMessage("tool-" + callsUsed, "tool", call.name() + " 结果：" + result, "now"));
                toolCalls.add(call.name());
                callsUsed++;
            }
            answer = aiClient.chat(context, question, model);
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
