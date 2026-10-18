package com.example.qa.service;

import com.example.qa.model.QuestionRequest;
import com.example.qa.model.QuestionResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QuestionService {
    public QuestionResponse ask(QuestionRequest request) {
        var answer = "这是 Spring Boot REST API 的演示回答。\n\n你刚刚问的是：**" + request.question().trim() + "**";
        var data = new QuestionResponse.Data(request.conversationId(), UUID.randomUUID().toString(), request.question().trim(), answer, List.of(), Instant.now());
        return QuestionResponse.success(data);
    }
}
