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
        var calls = firstParse.calls();
        // 只有真的发起工具调用时才保留 lead-in。普通回答的 naturalLanguage 就是 answer 本身，
        // 提前追加会导致最终答案重复两遍。
        if (calls.isEmpty()) return finalize(answer, leadIn, toolCalls, thinking);
        if (!firstParse.naturalLanguage().isEmpty()) leadIn.append(firstParse.naturalLanguage()).append("\n\n");

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
                if ("read_document".equals(call.name()) && arguments instanceof ObjectNode object) {
                    // 只允许使用本次请求中由 Electron 文件选择器授权的文档。
                    // 先清掉模型自己编造的 documents/document，避免模型伪造文件内容。
                    object.remove("documents");
                    object.remove("document");
                    var authorized = authorizedDocuments(document, documents);
                    if (authorized != null) object.set("documents", authorized);
                }
                var result = registry.execute(call.name(), arguments);
                traceWriter.write(java.util.Map.of("traceId", traceId, "type", "tool_result", "name", call.name(), "arguments", arguments, "result", result));
                // Chat Completions 的 tool 消息必须带 tool_call_id 且前面要有 assistant tool_calls。
                // 本实现使用自定义 JSON 协议，因此这里用 assistant + user 两条普通消息回填，
                // 既不伪造原生 tool_calls，也不会触发 provider 的 422。
                context.add(new ChatMessage("assistant-tool-" + callsUsed, "assistant",
                        "调用工具 " + call.name() + "，参数：" + describeArguments(call.name(), arguments), "now"));
                context.add(new ChatMessage("tool-" + callsUsed, "user",
                        "工具 " + call.name() + " 返回：\n" + result, "now"));
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
            var nextCalls = nextParse.calls();
            if (nextCalls.isEmpty()) return finalize(nextAnswer, leadIn, toolCalls, thinking);
            if (!nextParse.naturalLanguage().isEmpty()) leadIn.append(nextParse.naturalLanguage()).append("\n\n");
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

    /**
     * 只把请求里真实存在的授权文档传给工具；单文件旧字段也会转成数组。
     */
    private static JsonNode authorizedDocuments(JsonNode document, JsonNode documents) {
        if (documents != null && documents.isArray() && !documents.isEmpty()) return documents;
        if (document != null && document.isObject()) {
            var array = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            array.add(document);
            return array;
        }
        return null;
    }

    /**
     * 回填给模型的参数摘要。文档正文可能很大，不能直接塞进 assistant 消息。
     */
    private static String describeArguments(String toolName, JsonNode arguments) {
        if ("read_document".equals(toolName)) {
            var filename = arguments == null ? "" : arguments.path("filename").asText("");
            return filename.isBlank() ? "读取已授权的文档" : "filename=" + filename;
        }
        var text = arguments == null ? "" : arguments.toString();
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }
}
