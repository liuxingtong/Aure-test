package com.example.qa.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QuestionRequest(
        @NotBlank String conversationId,
        @NotBlank @Size(max = 10000) String question,
        @Valid List<ChatMessage> history) {
    public QuestionRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
