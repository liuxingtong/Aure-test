package com.example.qa.ai;

import com.example.qa.model.ChatMessage;
import java.util.List;

public interface AiClient {
    String chat(List<ChatMessage> history, String question);

    default String chat(List<ChatMessage> history, String question, String model) {
        return chat(history, question);
    }

    /**
     * 发送消息并返回包含思考字段的结果。默认实现回退到 chat()，不提供 thinking。
     * ProviderAdapterAiClient 会在底层支持 reasoning_content 时返回非空 thinking。
     */
    default ChatResult chatWithThinking(List<ChatMessage> history, String question, String model) {
        return new ChatResult(chat(history, question, model), "");
    }
}
