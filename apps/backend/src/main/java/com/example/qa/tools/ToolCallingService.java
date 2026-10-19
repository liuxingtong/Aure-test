package com.example.qa.tools;

import com.example.qa.ai.AiClient;
import com.example.qa.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.qa.monitoring.TraceWriter;

@Service
public class ToolCallingService {
    static final int MAX_CALLS = 6;
    private final AiClient aiClient;
    private final ToolRegistry registry;
    private final ToolCallParser parser;
    private final TraceWriter traceWriter;

    public record AnswerResult(String answer, List<String> toolCalls, String thinking) {
        public AnswerResult(String answer, List<String> toolCalls) { this(answer, toolCalls, ""); }
    }

    public ToolCallingService(AiClient aiClient, ToolRegistry registry, ObjectMapper mapper) {
        this(aiClient, registry, mapper, new TraceWriter((java.nio.file.Path) null, mapper));
    }

    @Autowired
    public ToolCallingService(AiClient aiClient, ToolRegistry registry, ObjectMapper mapper, TraceWriter traceWriter) {
        this.aiClient = aiClient;
        this.registry = registry;
        this.parser = new ToolCallParser(mapper);
        this.traceWriter = traceWriter;
    }

    public String answer(List<ChatMessage> history, String question) {
        return answerWithTools(history, question, null).answer();
    }

    public AnswerResult answerWithTools(List<ChatMessage> history, String question, String model) {
        return answerWithTools(history, question, model, null, null);
    }

    public AnswerResult answerWithTools(List<ChatMessage> history, String question, String model, JsonNode document) {
        return answerWithTools(history, question, model, document, null);
    }

    public AnswerResult answerWithTools(List<ChatMessage> history, String question, String model, JsonNode document, JsonNode documents) {
        if (isSearchRequest(question)) {
            var result = registry.execute("search_latest_news", new ObjectMapper().createObjectNode().put("query", question.trim()));
            return new AnswerResult(result, List.of("search_latest_news"));
        }
        var context = new ArrayList<>(history == null ? List.of() : history);
        var traceId = java.util.UUID.randomUUID().toString();

        var firstTurn = aiClient.chatWithThinking(context, question, model);
        var answer = firstTurn.content();
        var thinking = new StringBuilder(firstTurn.thinking() == null ? "" : firstTurn.thinking());
        traceWriter.write(java.util.Map.of("traceId", traceId, "type", "model_output", "model", model == null ? "" : model, "question", question, "history", context, "content", answer, "thinking", thinking.toString()));

        var callsUsed = 0;
        var toolCalls = new ArrayList<String>();
        // 累计非工具调用的自然语言（如 "我先算一下"）
        var leadIn = new StringBuilder();

        // 1) 解析首轮输出
        var firstParse = parser.parse(answer);
        if (!firstParse.naturalLanguage().isEmpty()) leadIn.append(firstParse.naturalLanguage()).append("\n\n");
        var calls = firstParse.calls();
        if (calls.isEmpty()) return finalize(answer, leadIn, toolCalls, thinking);

        // 2) 工具循环
        while (true) {
            if (callsUsed + calls.size() > MAX_CALLS) {
                // 用尽额度：插入一条"必须直接给答案"的硬约束，再做一次调用
                context.add(new ChatMessage("system-reminder-" + callsUsed, "system",
                        "你已用完工具调用额度。请不要再调用任何工具，基于已有信息直接用中文给出最终答案。", "now"));
                var finalTurn = aiClient.chatWithThinking(context, question, model);
                var finalAnswer = finalTurn.content();
                if (finalTurn.thinking() != null && !finalTurn.thinking().isBlank()) {
                    if (thinking.length() > 0) thinking.append("\n\n---\n\n");
                    thinking.append(finalTurn.thinking());
                }
                traceWriter.write(java.util.Map.of("traceId", traceId, "type", "model_output", "model", model == null ? "" : model, "question", question, "history", context, "content", finalAnswer, "thinking", thinking.toString(), "forced", true));
                return finalize(finalAnswer, leadIn, toolCalls, thinking);
            }

            for (var call : calls) {
                var arguments = call.arguments();
                if ("read_document".equals(call.name())) {
                    if (documents != null && !documents.isMissingNode() && !documents.isNull()) {
                        if (arguments instanceof ObjectNode object) object.set("documents", documents);
                    } else if (document != null && arguments instanceof ObjectNode object) {
                        object.set("document", document);
                    }
                }
                var result = registry.execute(call.name(), arguments);
                traceWriter.write(java.util.Map.of("traceId", traceId, "type", "tool_result", "name", call.name(), "arguments", arguments, "result", result));
                context.add(new ChatMessage("tool-" + callsUsed, "tool", call.name() + " 结果：" + result, "now"));
                toolCalls.add(call.name());
                callsUsed++;
            }

            var nextTurn = aiClient.chatWithThinking(context, question, model);
            var nextAnswer = nextTurn.content();
            if (nextTurn.thinking() != null && !nextTurn.thinking().isBlank()) {
                if (thinking.length() > 0) thinking.append("\n\n---\n\n");
                thinking.append(nextTurn.thinking());
            }
            traceWriter.write(java.util.Map.of("traceId", traceId, "type", "model_output", "model", model == null ? "" : model, "question", question, "history", context, "content", nextAnswer, "thinking", thinking.toString()));

            var nextParse = parser.parse(nextAnswer);
            if (!nextParse.naturalLanguage().isEmpty()) leadIn.append(nextParse.naturalLanguage()).append("\n\n");
            var nextCalls = nextParse.calls();
            if (nextCalls.isEmpty()) return finalize(nextAnswer, leadIn, toolCalls, thinking);
            calls = nextCalls;
            answer = nextAnswer;
        }
    }

    /**
     * 把累计的 lead-in 文字（如果有）和最终模型输出拼成完整答案返回。
     */
    private AnswerResult finalize(String answer, StringBuilder leadIn, List<String> toolCalls, StringBuilder thinking) {
        var leadText = leadIn.toString().trim();
        var body = answer == null ? "" : answer.trim();
        var combined = leadText.isEmpty() ? body : (leadText + "\n\n" + body);
        return new AnswerResult(combined, List.copyOf(toolCalls), thinking.toString());
    }

    private boolean isSearchRequest(String question) {
        if (question == null || question.isBlank()) return false;
        var normalized = question.toLowerCase();
        return normalized.contains("搜索") || normalized.contains("联网") || normalized.contains("新闻")
                || normalized.contains("查一下") || normalized.contains("查找") || normalized.contains("latest news")
                || normalized.contains("web search") || normalized.contains("browse");
    }
}
