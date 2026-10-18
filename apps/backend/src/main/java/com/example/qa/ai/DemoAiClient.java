package com.example.qa.ai;

import com.example.qa.model.ChatMessage;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(AiClient.class)
public class DemoAiClient implements AiClient {
    @Override
    public String chat(List<ChatMessage> history, String question) {
        return MarkdownNormalizer.normalize("这是 Spring Boot REST API 的演示回答。\n\n你刚刚问的是：**" + question.trim() + "**");
    }
}
