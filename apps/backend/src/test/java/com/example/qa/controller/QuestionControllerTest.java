package com.example.qa.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qa.service.QuestionService;
import com.example.qa.ai.DemoAiClient;
import com.example.qa.tools.CalculateTool;
import com.example.qa.tools.SearchLatestNewsTool;
import com.example.qa.tools.ToolCallingService;
import com.example.qa.tools.ToolRegistry;
import com.example.qa.cache.QuestionCache;
import com.example.qa.monitoring.QaMetrics;
import com.example.qa.monitoring.RequestSizeFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuestionController.class)
@Import({QuestionService.class, DemoAiClient.class, ToolCallingService.class, ToolRegistry.class, CalculateTool.class, SearchLatestNewsTool.class, QuestionCache.class, QaMetrics.class, RequestSizeFilter.class})
class QuestionControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void acceptsQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"question\":\"什么是 Spring Boot？\",\"history\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.data.answer").isString());
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"question\":\"  \",\"history\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsTooManyHistoryMessages() throws Exception {
        var history = "[" + "{\"id\":\"m\",\"role\":\"user\",\"content\":\"x\",\"createdAt\":\"now\"},".repeat(101);
        history = history.substring(0, history.length() - 1) + "]";
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"question\":\"hello\",\"history\":" + history + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void reportsMalformedJsonAsNonRetryableValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.retryable").value(false));
    }

    @Test
    void rejectsAnOversizedRequestBody() throws Exception {
        var message = "{\"id\":\"m\",\"role\":\"user\",\"content\":\"" + "x".repeat(20000) + "\",\"createdAt\":\"now\"}";
        var history = "[" + (message + ",").repeat(55);
        history = history.substring(0, history.length() - 1) + "]";
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"question\":\"hello\",\"history\":" + history + "}"))
                .andExpect(status().isPayloadTooLarge());
    }
}
