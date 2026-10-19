package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownDocumentToolTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private MarkdownDocumentTool tool(Path outputDir) {
        return new MarkdownDocumentTool(new MarkdownOutputWriter(outputDir));
    }

    @Test
    void rendersContentListTableAndCodeInStableOrder(@TempDir Path tmp) throws Exception {
        var result = tool(tmp).execute(mapper.readTree("""
                {"title":"说明","content":"正文","list":["一","二"],
                 "table":{"headers":["名称","值"],"rows":[["a|b","1"]]},
                 "code":{"language":"java","content":"System.out.println(1);"}}
                """));

        assertThat(result).startsWith("已保存到 [");
        assertThat(result).contains("""
                # 说明

                正文

                - 一
                - 二

                | 名称 | 值 |
                | --- | --- |
                | a\\|b | 1 |

                ```java
                System.out.println(1);
                ```
                """);
        assertThat(result).contains("saved_path: " + tmp.resolve("yunhe"));
        assertThat(result).contains("file://" + tmp.resolve("yunhe"));
    }

    @Test
    void writesFileToDisk(@TempDir Path tmp) throws Exception {
        var result = tool(tmp).execute(mapper.readTree("{\"title\":\"问候\",\"content\":\"你好\"}"));
        var written = Files.list(tmp.resolve("yunhe")).findFirst().orElseThrow();
        var content = Files.readString(written);
        assertThat(content).contains("# 问候", "你好");
        assertThat(result).contains(written.toString());
    }

    @Test
    void allowsAnySingleContentBlock(@TempDir Path tmp) throws Exception {
        var input = mapper.createObjectNode().put("title", "列表");
        input.putArray("list").add("项目");
        assertThat(tool(tmp).execute(input))
                .contains("# 列表", "- 项目");
    }

    @Test
    void rejectsBlankTitleAndMissingContentBlocks(@TempDir Path tmp) {
        assertThatThrownBy(() -> tool(tmp).execute(mapper.createObjectNode().put("title", "")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> tool(tmp).execute(mapper.createObjectNode().put("title", "标题")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("content");
    }

    @Test
    void rejectsMalformedTableAndListArguments(@TempDir Path tmp) {
        var table = mapper.createObjectNode().put("title", "表");
        table.putObject("table").put("headers", "bad").putArray("rows");
        assertThatThrownBy(() -> tool(tmp).execute(table))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("table");
        var list = mapper.createObjectNode().put("title", "列").put("list", "bad");
        assertThatThrownBy(() -> tool(tmp).execute(list))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("list");
    }
}
