package com.example.qa.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class QuestionCache {
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Autowired
    public QuestionCache(@Value("${cache.question-ttl:5m}") Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    public QuestionCache(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    public Optional<String> get(String key) {
        var entry = entries.get(key);
        if (entry == null) return Optional.empty();
        if (entry.expiresAt().isBefore(Instant.now(clock))) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.answer());
    }

    public void put(String key, String answer) {
        entries.put(key, new Entry(answer, Instant.now(clock).plus(ttl)));
    }

    private record Entry(String answer, Instant expiresAt) {}
}
