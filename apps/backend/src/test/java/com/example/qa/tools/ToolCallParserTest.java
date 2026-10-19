package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ToolCallParserTest {
    @Test
    void parsesSingleToolCallEnvelope() throws Exception {
        var parser = new ToolCallParser(new ObjectMapper());
        var result = parser.parse("{\"tool_calls\":[{\"name\":\"calculate\",\"arguments\":{\"expression\":\"2+2\"}}]}");

        assertThat(result.naturalLanguage()).isEmpty();
        assertThat(result.calls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("calculate");
            assertThat(call.arguments().get("expression").asText()).isEqualTo("2+2");
        });
    }

    @Test
    void recoversToolCallWhenToolCallsArrayIsNotClosed() throws Exception {
        var parser = new ToolCallParser(new ObjectMapper());
        // 模型实际生成过这种漏掉 tool_calls 数组最后一个 ] 的输出。
        var result = parser.parse("{\"tool_calls\":[{\"name\":\"generate_markdown\",\"arguments\":{\"title\":\"番茄\",\"content\":\"第一行\\n第二行\"}}}");

        assertThat(result.calls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("generate_markdown");
            assertThat(call.arguments().get("content").asText()).isEqualTo("第一行\n第二行");
        });
    }

    @Test
    void rejectsToolCallWhenArgumentsCannotBeRecovered() {
        var parser = new ToolCallParser(new ObjectMapper());
        assertThatThrownBy(() -> parser.parse("{\"tool_calls\":[{\"name\":\"calculate\",\"arguments\":{"))
                .isInstanceOf(ToolException.class)
                .extracting("code").isEqualTo("INVALID_TOOL_ARGUMENTS");
    }

    @Test
    void preservesNaturalLanguageBeforeAndAfterTheJsonEnvelope() throws Exception {
        var parser = new ToolCallParser(new ObjectMapper());
        var result = parser.parse("我先算一下。\n{\"tool_calls\":[{\"name\":\"calculate\",\"arguments\":{\"expression\":\"2+2\"}}]}\n稍等。");

        assertThat(result.naturalLanguage()).contains("我先算一下").contains("稍等");
        assertThat(result.calls()).hasSize(1);
    }

    @Test
    void returnsEmptyCallListWhenThereIsNoJson() throws Exception {
        var parser = new ToolCallParser(new ObjectMapper());
        var result = parser.parse("只是一个普通回答，没有任何工具调用。");

        assertThat(result.calls()).isEmpty();
        assertThat(result.naturalLanguage()).isEqualTo("只是一个普通回答，没有任何工具调用。");
    }
}
