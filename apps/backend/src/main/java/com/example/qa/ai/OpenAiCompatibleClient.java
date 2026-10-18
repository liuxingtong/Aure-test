package com.example.qa.ai;

import com.example.qa.model.ChatMessage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiProperties.class)
public class OpenAiCompatibleClient implements AiClient {
    private final RestClient client;
    private final AiProperties properties;
    private final PromptBuilder promptBuilder = new PromptBuilder();

    public OpenAiCompatibleClient(RestClient.Builder builder, AiProperties properties) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        var timeoutMillis = Math.toIntExact(properties.timeout().toMillis());
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        this.client = builder.requestFactory(factory).baseUrl(properties.baseUrl()).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String chat(List<ChatMessage> history, String question) {
        var messages = promptBuilder.build(history, question).stream().map(message -> Map.of("role", message.role(), "content", message.content())).toList();
        var body = Map.of("model", properties.model(), "messages", messages);
        Map<String, Object> response = client.post().uri("/chat/completions").header("Authorization", "Bearer " + properties.apiKey()).body(body).retrieve().body(Map.class);
        var choices = (List<Map<String, Object>>) response.getOrDefault("choices", List.of());
        if (choices.isEmpty()) throw new IllegalStateException("AI 返回结果为空");
        var message = (Map<String, Object>) choices.get(0).get("message");
        return MarkdownNormalizer.normalize(String.valueOf(message.getOrDefault("content", "暂无回答")));
    }
}
