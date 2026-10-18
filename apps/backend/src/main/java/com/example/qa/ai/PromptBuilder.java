package com.example.qa.ai;

import com.example.qa.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;

public class PromptBuilder {
    public List<ChatMessage> build(List<ChatMessage> history, String question) {
        var messages = new ArrayList<ChatMessage>();
        messages.add(new ChatMessage("system", "system", "你是一个严谨、简洁的中文 AI 助手。请使用 Markdown 输出答案。", "system"));
        messages.addAll(history == null ? List.of() : history);
        messages.add(new ChatMessage("question", "user", question.trim(), "now"));
        return List.copyOf(messages);
    }
}
