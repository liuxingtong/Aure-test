package com.example.qa.ai;

import com.example.qa.model.ChatMessage;
import java.util.List;

/** Unified protocol boundary for provider-specific AI integrations. */
public interface ProviderAdapter {
    String provider();
    String chat(List<ChatMessage> history, String question);

    default String chat(List<ChatMessage> history, String question, String model) {
        return chat(history, question);
    }

    /**
     * 发送消息并返回包含思考字段的结果。默认实现回退到 chat()，不提供 thinking。
     * 支持 reasoning_content / reasoning / reflection 字段的兼容服务应覆写此方法。
     */
    default ChatResult chatWithThinking(List<ChatMessage> history, String question, String model) {
        return new ChatResult(chat(history, question, model), "");
    }
}
