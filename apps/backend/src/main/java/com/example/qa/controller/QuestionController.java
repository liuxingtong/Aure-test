package com.example.qa.controller;

import com.example.qa.model.QuestionRequest;
import com.example.qa.model.QuestionResponse;
import com.example.qa.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {
    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) { this.questionService = questionService; }

    @PostMapping
    public ResponseEntity<QuestionResponse> ask(@Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(questionService.ask(request));
    }
}
