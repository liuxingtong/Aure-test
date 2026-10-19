package com.example.qa.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TraceWriter {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final Path directory;
    private final ObjectMapper mapper;

    public TraceWriter(Path directory, ObjectMapper mapper) {
        this.directory = directory;
        this.mapper = mapper;
    }

    @Autowired
    public TraceWriter(@Value("${ai.trace-directory:}") String directory, ObjectMapper mapper) {
        this(directory == null || directory.isBlank() ? null : Path.of(directory), mapper);
    }

    public synchronized void write(Map<String, ?> record) {
        if (directory == null) return;
        try {
            Files.createDirectories(directory);
            var value = new LinkedHashMap<String, Object>();
            value.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
            value.putAll(record);
            var line = mapper.writeValueAsString(value) + "\n";
            Files.writeString(directory.resolve(FILE_DATE.format(OffsetDateTime.now(ZoneOffset.UTC)) + ".jsonl"), line,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            // Tracing must never break a user request.
        }
    }
}
