package com.example.qa.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

public record QuestionRequest(
        @NotBlank @Size(max = 128) String conversationId,
        @NotBlank @Size(max = 10000) String question,
        @Size(max = 100) List<@Valid ChatMessage> history,
        @Size(max = 200) String model,
        JsonNode document,
        JsonNode documents) {
    public QuestionRequest(String conversationId, String question, List<ChatMessage> history, String model) {
        this(conversationId, question, history, model, null, null);
    }

    public QuestionRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
