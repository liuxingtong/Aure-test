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
    void supportsPowerFactorialAndTrigonometry() throws Exception {
        assertThat(tool.execute(mapper.readTree("{\"expression\":\"2^10\"}"))).isEqualTo("1024");
        assertThat(tool.execute(mapper.readTree("{\"expression\":\"5!\"}"))).isEqualTo("120");
        assertThat(tool.execute(mapper.readTree("{\"expression\":\"sqrt(144)\"}"))).isEqualTo("12");
        assertThat(tool.execute(mapper.readTree("{\"expression\":\"ln(e^2)\"}"))).isEqualTo("2");
        // sin(pi/3) ≈ 0.8660254
        var sinValue = Double.parseDouble(tool.execute(mapper.readTree("{\"expression\":\"sin(pi/3)\"}")));
        assertThat(sinValue).isCloseTo(Math.sqrt(3) / 2, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void rejectsUnsupportedExpression() throws Exception {
        // 仍拒绝带分号/花括号的输入
        assertThatThrownBy(() -> tool.execute(mapper.readTree("{\"expression\":\"2;System.exit(0)\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.execute(mapper.readTree("{\"expression\":\"eval('1+1')\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
