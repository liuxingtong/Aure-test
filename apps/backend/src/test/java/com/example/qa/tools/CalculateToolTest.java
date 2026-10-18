package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CalculateToolTest {
    private final CalculateTool tool = new CalculateTool();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void evaluatesBasicArithmeticWithParentheses() throws Exception {
        var result = tool.execute(mapper.readTree("{\"expression\":\"(2 + 3) * 4 / 2\"}"));

        assertThat(result).isEqualTo("10");
    }

    @Test
    void rejectsUnsupportedExpression() throws Exception {
        assertThatThrownBy(() -> tool.execute(mapper.readTree("{\"expression\":\"2 ^ 3\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
