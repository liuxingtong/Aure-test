package com.example.qa.service;

import com.example.qa.model.QuestionRequest;
import com.example.qa.model.QuestionResponse;
import com.example.qa.tools.ToolCallingService;
import com.example.qa.cache.QuestionCache;
import com.example.qa.monitoring.QaMetrics;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class QuestionService {
    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);
    private final ToolCallingService toolCallingService;
    private final QuestionCache cache;
    private final QaMetrics metrics;

    public QuestionService(ToolCallingService toolCallingService, QuestionCache cache, QaMetrics metrics) {
        this.toolCallingService = toolCallingService;
        this.cache = cache;
        this.metrics = metrics;
    }

    public QuestionResponse ask(QuestionRequest request) {
        metrics.question();
        var key = request.model() + "\n" + request.question().trim() + "\n" + request.history();
        var answer = cache.get(key).orElse(null);
        if (answer != null) {
            metrics.cacheHit();
            log.debug("question cache hit conversationId={}", request.conversationId());
        } else {
            try {
                log.info("processing question conversationId={} historySize={}", request.conversationId(), request.history().size());
                var result = toolCallingService.answerWithTools(request.history(), request.question(), request.model(), request.document(), request.documents());
                answer = result.answer();
                cache.put(key, answer);
                var toolCalls = result.toolCalls();
                var data = new QuestionResponse.Data(request.conversationId(), UUID.randomUUID().toString(), request.question().trim(), answer, toolCalls, Instant.now(), result.thinking());
                return QuestionResponse.success(data);
            } catch (RuntimeException exception) {
                metrics.failure();
                log.warn("question processing failed conversationId={} type={}", request.conversationId(), exception.getClass().getSimpleName());
                throw exception;
            }
        }
        var data = new QuestionResponse.Data(request.conversationId(), UUID.randomUUID().toString(), request.question().trim(), answer, List.of(), Instant.now());
        return QuestionResponse.success(data);
    }
}
