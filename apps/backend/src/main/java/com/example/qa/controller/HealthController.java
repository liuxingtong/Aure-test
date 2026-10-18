package com.example.qa.controller;

import java.util.Map;
import com.example.qa.ai.AiProperties;
import com.example.qa.monitoring.QaMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    private final AiProperties aiProperties;
    private final QaMetrics metrics;

    public HealthController(AiProperties aiProperties, QaMetrics metrics) {
        this.aiProperties = aiProperties;
        this.metrics = metrics;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "aiService", aiProperties.enabled() ? "CONFIGURED" : "DEMO", "version", "0.1.0",
                "metrics", Map.of("totalQuestions", metrics.totalQuestions(), "cacheHits", metrics.cacheHits(), "failures", metrics.failures()));
    }
}
