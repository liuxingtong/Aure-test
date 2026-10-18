package com.example.qa.monitoring;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class QaMetrics {
    private final AtomicLong totalQuestions = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public void question() { totalQuestions.incrementAndGet(); }
    public void cacheHit() { cacheHits.incrementAndGet(); }
    public void failure() { failures.incrementAndGet(); }
    public long totalQuestions() { return totalQuestions.get(); }
    public long cacheHits() { return cacheHits.get(); }
    public long failures() { return failures.get(); }
}
