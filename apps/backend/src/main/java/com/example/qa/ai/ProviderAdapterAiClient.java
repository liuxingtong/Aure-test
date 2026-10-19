package com.example.qa.ai;

import com.example.qa.model.ChatMessage;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true")
public class ProviderAdapterAiClient implements AiClient {
    private final AiProperties properties;
    private final List<ProviderAdapter> adapters;

    public ProviderAdapterAiClient(AiProperties properties, List<ProviderAdapter> adapters) {
        this.properties = properties;
        this.adapters = adapters;
    }

    @Override
    public String chat(List<ChatMessage> history, String question) {
        return chat(history, question, null);
    }

    @Override
    public String chat(List<ChatMessage> history, String question, String model) {
        return chatWithThinking(history, question, model).content();
    }

    @Override
    public ChatResult chatWithThinking(List<ChatMessage> history, String question, String model) {
        return adapters.stream().filter(adapter -> adapter.provider().equals(properties.provider())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的 AI Provider: " + properties.provider()))
                .chatWithThinking(history, question, model);
    }
}
