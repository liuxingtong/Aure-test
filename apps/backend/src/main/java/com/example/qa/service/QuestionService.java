package com.example.qa.service;

import com.example.qa.model.QuestionRequest;
import com.example.qa.model.QuestionResponse;
import com.example.qa.ai.AiClient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QuestionService {
    private final AiClient aiClient;

    public QuestionService(AiClient aiClient) { this.aiClient = aiClient; }

    public QuestionResponse ask(QuestionRequest request) {
        var answer = aiClient.chat(request.history(), request.question());
        var data = new QuestionResponse.Data(request.conversationId(), UUID.randomUUID().toString(), request.question().trim(), answer, List.of(), Instant.now());
        return QuestionResponse.success(data);
    }
}
