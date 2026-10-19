package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class ToolRegistryTest {
    @Test
    void registersRequiredToolsAndExecutesCalculate() throws Exception {
        var registry = TestToolRegistry.create();

        assertThat(registry.names()).containsExactly("calculate", "search_latest_news", "create_plan", "generate_markdown", "read_document");
        assertThat(registry.execute("calculate", new ObjectMapper().readTree("{\"expression\":\"1+2\"}")))
                .isEqualTo("3");
    }

    @Test
    void createsExternalNewsSearchLinks() throws Exception {
        var tool = new SearchLatestNewsTool();

        assertThat(tool.execute(new ObjectMapper().readTree("{\"query\":\"人工智能\"}")))
                .contains("https://news.google.com/search?q=", "https://www.bing.com/news/search?q=");
    }

    @Test
    void generatesPlanAndMarkdown(@TempDir Path tmp) throws Exception {
        var mapper = new ObjectMapper();
        var registry = TestToolRegistry.create(tmp);
        assertThat(registry.execute("create_plan", mapper.readTree("{\"goal\":\"发布应用\"}"))).contains("执行计划");
        assertThat(registry.execute("generate_markdown", mapper.readTree("{\"title\":\"说明\",\"content\":\"正文\"}")))
                .contains("# 说明", "正文", "saved_path:");
    }
}
