package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {
    @Test
    void registersRequiredToolsAndExecutesCalculate() throws Exception {
        var registry = new ToolRegistry(new CalculateTool(), new SearchLatestNewsTool());

        assertThat(registry.names()).containsExactly("calculate", "search_latest_news");
        assertThat(registry.execute("calculate", new ObjectMapper().readTree("{\"expression\":\"1+2\"}")))
                .isEqualTo("3");
    }
}
