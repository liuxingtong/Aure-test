package com.example.qa.ai;

import com.example.qa.model.ChatMessage;
import java.util.List;

/** Unified protocol boundary for provider-specific AI integrations. */
public interface ProviderAdapter {
    String provider();
    String chat(List<ChatMessage> history, String question);
}
