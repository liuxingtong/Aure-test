package com.example.qa.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class QuestionCacheTest {
    @Test
    void returnsValueUntilTtlExpires() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var cache = new QuestionCache(Duration.ofSeconds(30), clock);
        cache.put("key", "answer");

        assertThat(cache.get("key")).contains("answer");
        clock.now = clock.now.plusSeconds(31);
        assertThat(cache.get("key")).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
