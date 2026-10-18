package com.example.qa.service;

import com.example.qa.model.QuestionRequest;
import com.example.qa.model.QuestionResponse;
import com.example.qa.tools.ToolCallingService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QuestionService {
    private final ToolCallingService toolCallingService;

    public QuestionService(ToolCallingService toolCallingService) { this.toolCallingService = toolCallingService; }

    public QuestionResponse ask(QuestionRequest request) {
        var answer = toolCallingService.answer(request.history(), request.question());
        var data = new QuestionResponse.Data(request.conversationId(), UUID.randomUUID().toString(), request.question().trim(), answer, List.of(), Instant.now());
        return QuestionResponse.success(data);
    }
}
