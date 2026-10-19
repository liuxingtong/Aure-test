package com.example.qa.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TraceWriter {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "question", "content", "history", "messages", "arguments", "result", "raw", "thinking", "reasoning_content");
    private final Path directory;
    private final ObjectMapper mapper;
    private final boolean redact;

    public TraceWriter(Path directory, ObjectMapper mapper) {
        this(directory, mapper, false);
    }

    @Autowired
    public TraceWriter(@Value("${ai.trace-directory:}") String directory,
                       @Value("${ai.trace-content:false}") boolean includeContent,
                       ObjectMapper mapper) {
        this(directory == null || directory.isBlank() ? null : Path.of(directory), mapper, !includeContent);
    }

    private TraceWriter(Path directory, ObjectMapper mapper, boolean redact) {
        this.directory = directory;
        this.mapper = mapper;
        this.redact = redact;
    }

    public synchronized void write(Map<String, ?> record) {
        if (directory == null) return;
        try {
            Files.createDirectories(directory);
            var value = new LinkedHashMap<String, Object>();
            value.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
            for (var entry : record.entrySet()) {
                value.put(entry.getKey(), redact && SENSITIVE_KEYS.contains(entry.getKey())
                        ? summarize(entry.getValue())
                        : entry.getValue());
            }
            var line = mapper.writeValueAsString(value) + "\n";
            Files.writeString(directory.resolve(FILE_DATE.format(OffsetDateTime.now(ZoneOffset.UTC)) + ".jsonl"), line,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            // Tracing must never break a user request.
        }
    }

    /**
     * 默认不把问题、答案、文档正文和完整 prompt 写盘；只保留长度/条数用于排查。
     * 需要完整内容时显式设置 AI_TRACE_CONTENT=true。
     */
    private static Object summarize(Object value) {
        if (value == null) return null;
        if (value instanceof CharSequence text) return "[redacted:" + text.length() + " chars]";
        if (value instanceof Collection<?> items) return "[redacted:" + items.size() + " items]";
        if (value instanceof Map<?, ?> map) return "[redacted:" + map.size() + " fields]";
        return "[redacted]";
    }
}
