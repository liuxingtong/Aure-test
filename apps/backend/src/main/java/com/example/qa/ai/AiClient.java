package com.example.qa.ai;

import com.example.qa.model.ChatMessage;
import java.util.List;

public interface AiClient {
    String chat(List<ChatMessage> history, String question);
}
