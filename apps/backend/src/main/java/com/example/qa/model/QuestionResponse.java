package com.example.qa.model;

import java.time.Instant;
import java.util.List;

public record QuestionResponse(boolean success, Data data, Error error) {
    public record Data(String conversationId, String messageId, String question, String answer, List<String> toolCalls, Instant createdAt) {}
    public record Error(String code, String message, boolean retryable) {}

    public static QuestionResponse success(Data data) { return new QuestionResponse(true, data, null); }
    public static QuestionResponse failure(String code, String message, boolean retryable) { return new QuestionResponse(false, null, new Error(code, message, retryable)); }
}
