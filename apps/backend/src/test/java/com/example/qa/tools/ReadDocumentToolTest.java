package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ReadDocumentToolTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReadDocumentTool tool = new ReadDocumentTool();

    @Test
    void asksElectronForSelectionWhenNoAuthorizedDocumentExists() {
        assertThatThrownBy(() -> tool.execute(mapper.createObjectNode()))
                .isInstanceOf(ToolException.class)
                .extracting("code").isEqualTo("DOCUMENT_SELECTION_REQUIRED");
    }

    @Test
    void returnsOnlyAuthorizedDocumentContent() throws Exception {
        var args = mapper.createObjectNode();
        args.putObject("document").put("name", "notes.md").put("extension", "md").put("content", "# Notes");

        assertThat(tool.execute(args)).contains("notes.md", "# Notes");
    }

    @Test
    void supportsMultipleAuthorizedDocuments() throws Exception {
        var args = mapper.createObjectNode();
        var docs = args.putArray("documents");
        docs.addObject().put("name", "a.md").put("extension", "md").put("content", "# A");
        docs.addObject().put("name", "b.txt").put("extension", "txt").put("content", "B");

        assertThat(tool.execute(args)).contains("a.md", "b.txt", "预览");
        args.put("filename", "a");
        assertThat(tool.execute(args)).contains("# A");
    }

    @Test
    void summaryDoesNotDuplicateTheFileExtension() {
        var args = mapper.createObjectNode();
        args.putArray("documents").addObject().put("name", "notes.md").put("extension", "md").put("content", "# Notes");

        assertThat(tool.execute(args)).contains("notes.md").doesNotContain("notes.md.md");
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
