package com.example.qa.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.qa.monitoring.TraceWriter;

@Component
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true")
public class DeepSeekProviderAdapter extends AbstractChatCompletionsAdapter {
    public DeepSeekProviderAdapter(RestClient.Builder builder, AiProperties properties, ObjectMapper mapper, TraceWriter traceWriter) { super(builder, properties, mapper, traceWriter); }
    public String provider() { return "deepseek"; }
}
