package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {
    @Test
    void registersRequiredToolsAndExecutesCalculate() throws Exception {
        var registry = new ToolRegistry(new CalculateTool(), new SearchLatestNewsTool());

        assertThat(registry.names()).containsExactly("calculate", "search_latest_news", "create_plan", "generate_markdown");
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
    void generatesPlanAndMarkdown() throws Exception {
        var mapper = new ObjectMapper();
        var registry = new ToolRegistry(new CalculateTool(), new SearchLatestNewsTool(), new PlanTool(), new MarkdownDocumentTool());
        assertThat(registry.execute("create_plan", mapper.readTree("{\"goal\":\"发布应用\"}"))).contains("执行计划");
        assertThat(registry.execute("generate_markdown", mapper.readTree("{\"title\":\"说明\",\"content\":\"正文\"}"))).isEqualTo("# 说明\n\n正文\n");
    }
}
