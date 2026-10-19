package com.example.qa.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true")
public class OpenAiCompatibleProviderAdapter extends AbstractChatCompletionsAdapter {
    public OpenAiCompatibleProviderAdapter(RestClient.Builder builder, AiProperties properties) { super(builder, properties); }
    public String provider() { return "openai-compatible"; }
}
