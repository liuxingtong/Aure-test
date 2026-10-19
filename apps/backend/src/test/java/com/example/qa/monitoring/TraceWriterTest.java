package com.example.qa.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TraceWriterTest {
    @Test
    void appendsCompleteJsonlRecordsWithoutCredentials() throws Exception {
        Path directory = Files.createTempDirectory("qa-traces");
        var writer = new TraceWriter(directory, new ObjectMapper());

        writer.write(Map.of("type", "model_response", "content", "第一行\n第二行", "reasoning_content", "完整思考"));

        var files = Files.list(directory).toList();
        assertThat(files).hasSize(1);
        var line = Files.readString(files.get(0));
        assertThat(line).contains("第一行\\n第二行", "完整思考").doesNotContain("apiKey", "Authorization");
        assertThat(new ObjectMapper().readTree(line).path("content").asText()).isEqualTo("第一行\n第二行");
    }
}
