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
    static final int MAX_ENTRIES = 256;
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
        var now = Instant.now(clock);
        // 过期项只在 get 时删除，长期只写不读的 key 会一直占内存。
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        if (entries.size() >= MAX_ENTRIES) {
            entries.keySet().stream().findFirst().ifPresent(entries::remove);
        }
        entries.put(key, new Entry(answer, now.plus(ttl)));
    }

    private record Entry(String answer, Instant expiresAt) {}
}
