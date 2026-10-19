package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ToolErrorTest {
    @Test
    void classifiesUnknownToolAndInvalidArguments() throws Exception {
        var registry = TestToolRegistry.create();
        assertThatThrownBy(() -> registry.execute("missing", new ObjectMapper().createObjectNode()))
                .isInstanceOf(ToolException.class)
                .extracting("code").isEqualTo("UNKNOWN_TOOL");
        assertThatThrownBy(() -> registry.execute("calculate", new ObjectMapper().createObjectNode()))
                .isInstanceOf(ToolException.class)
                .extracting("code").isEqualTo("INVALID_TOOL_ARGUMENTS");
    }
}
