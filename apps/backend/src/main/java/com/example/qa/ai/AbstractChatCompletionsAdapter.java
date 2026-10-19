package com.example.qa.ai;

import com.example.qa.model.ChatMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.example.qa.monitoring.TraceWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractChatCompletionsAdapter implements ProviderAdapter {
    private static final Logger log = LoggerFactory.getLogger(AbstractChatCompletionsAdapter.class);
    private final RestClient client;
    private final AiProperties properties;
    private final PromptBuilder promptBuilder = new PromptBuilder();
    private final ObjectMapper mapper;
    private final TraceWriter traceWriter;

    protected AbstractChatCompletionsAdapter(RestClient.Builder builder, AiProperties properties, ObjectMapper mapper, TraceWriter traceWriter) {
        this.properties = properties;
        this.mapper = mapper;
        this.traceWriter = traceWriter;
        var factory = new SimpleClientHttpRequestFactory();
        var timeoutMillis = Math.toIntExact(properties.timeout().toMillis());
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        this.client = builder.requestFactory(factory).baseUrl(properties.baseUrl()).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String chat(List<ChatMessage> history, String question) {
        return chatWithThinking(history, question, null).content();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String chat(List<ChatMessage> history, String question, String model) {
        return chatWithThinking(history, question, model).content();
    }

    public ChatResult chatWithThinking(List<ChatMessage> history, String question, String model) {
        var messages = promptBuilder.build(history, question).stream()
                .map(message -> Map.of("role", message.role(), "content", message.content())).toList();
        var body = Map.of("model", model == null || model.isBlank() ? properties.model() : model, "messages", messages);
        var traceId = java.util.UUID.randomUUID().toString();
        traceWriter.write(Map.of("traceId", traceId, "type", "model_request", "provider", properties.provider(), "model", body.get("model"), "messages", messages));
        var rawResponse = client.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + properties.apiKey()).body(body).retrieve()
                .onStatus(status -> status.isError(), (request, responseMessage) -> {
                    var httpStatus = responseMessage.getStatusCode().value();
                    var errorBody = new String(responseMessage.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    var loggedBody = errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody;
                    log.warn("provider request failed status={} body={}", httpStatus, loggedBody);
                    throw new ProviderHttpException(httpStatus, providerMessage(httpStatus));
                }).body(String.class);
        traceWriter.write(Map.of("traceId", traceId, "type", "model_response", "provider", properties.provider(), "model", body.get("model"), "raw", rawResponse));
        try {
            var response = mapper.readTree(rawResponse);
            var choices = response.path("choices");
            if (!choices.isArray() || choices.isEmpty()) throw new IllegalStateException("AI 返回结果为空");
            var message = choices.get(0).path("message");
            var content = MarkdownNormalizer.normalize(message.path("content").asText(""));
            var thinking = extractThinking(message);
            if (!thinking.isBlank()) {
                traceWriter.write(Map.of("traceId", traceId, "type", "model_thinking", "provider", properties.provider(), "thinking", thinking));
            }
            return new ChatResult(content, thinking);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("AI 返回 JSON 无效", exception);
        }
    }

    private static String providerMessage(int status) {
        return switch (status) {
            case 401, 403 -> "API Key 无效或没有访问权限，请在账户设置中检查。";
            case 404 -> "接口地址或模型不存在，请检查账户设置中的接口地址和模型名称。";
            case 429 -> "AI 服务请求过于频繁，请稍后重试。";
            case 400, 422 -> "AI 服务拒绝了请求，请检查模型名称或输入内容。";
            default -> status >= 500
                    ? "AI 服务暂时不可用，请稍后重试。"
                    : "AI 服务返回 HTTP " + status + "，请检查账户设置。";
        };
    }

    /**
     * 提取模型思考字段。兼容 deepseek-reasoner 等会把推理放在 reasoning_content 的服务，
     * 以及把思考放在 reasoning/reflection 字段的兼容服务。
     */
    private String extractThinking(JsonNode message) {
        var thinking = message.path("reasoning_content");
        if (thinking.isMissingNode() || thinking.isNull() || thinking.asText().isBlank()) {
            thinking = message.path("reasoning");
        }
        if (thinking.isMissingNode() || thinking.isNull() || thinking.asText().isBlank()) {
            thinking = message.path("reflection");
        }
        return thinking.isMissingNode() || thinking.isNull() ? "" : thinking.asText();
    }
}
