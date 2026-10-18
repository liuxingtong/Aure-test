package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ToolCallParserTest {
    @Test
    void parsesSingleToolCallEnvelope() throws Exception {
        var parser = new ToolCallParser(new ObjectMapper());
        var calls = parser.parse("{\"tool_calls\":[{\"name\":\"calculate\",\"arguments\":{\"expression\":\"2+2\"}}]}");

        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("calculate");
            assertThat(call.arguments().get("expression").asText()).isEqualTo("2+2");
        });
    }
}
