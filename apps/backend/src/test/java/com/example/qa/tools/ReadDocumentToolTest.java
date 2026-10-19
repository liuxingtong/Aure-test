package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ReadDocumentToolTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReadDocumentTool tool = new ReadDocumentTool();

    @Test
    void asksElectronForSelectionWhenNoAuthorizedDocumentExists() throws Exception {
        assertThat(tool.execute(mapper.createObjectNode()))
                .contains("DOCUMENT_SELECTION_REQUIRED");
    }

    @Test
    void returnsOnlyAuthorizedDocumentContent() throws Exception {
        var args = mapper.createObjectNode();
        args.putObject("document").put("name", "notes.md").put("extension", "md").put("content", "# Notes");

        assertThat(tool.execute(args)).contains("notes.md", "# Notes");
    }

    @Test
    void rejectsPathArgumentsAndUnsupportedExtensions() throws Exception {
        assertThatThrownBy(() -> tool.execute(mapper.createObjectNode().put("path", "/tmp/secret.txt")))
                .isInstanceOf(ToolException.class).extracting("code").isEqualTo("INVALID_TOOL_ARGUMENTS");
        var args = mapper.createObjectNode();
        args.putObject("document").put("name", "secret.exe").put("extension", "exe").put("content", "x");
        assertThatThrownBy(() -> tool.execute(args)).isInstanceOf(ToolException.class).extracting("code").isEqualTo("INVALID_TOOL_ARGUMENTS");
    }
}
