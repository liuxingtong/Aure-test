package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MarkdownDocumentToolTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MarkdownDocumentTool tool = new MarkdownDocumentTool();

    @Test
    void rendersContentListTableAndCodeInStableOrder() throws Exception {
        var result = tool.execute(mapper.readTree("""
                {"title":"说明","content":"正文","list":["一","二"],
                 "table":{"headers":["名称","值"],"rows":[["a|b","1"]]},
                 "code":{"language":"java","content":"System.out.println(1);"}}
                """));

        assertThat(result).isEqualTo("""
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
    }

    @Test
    void allowsAnySingleContentBlock() throws Exception {
        var input = mapper.createObjectNode().put("title", "列表");
        input.putArray("list").add("项目");
        assertThat(tool.execute(input))
                .contains("# 列表", "- 项目");
    }

    @Test
    void rejectsBlankTitleAndMissingContentBlocks() throws Exception {
        assertThatThrownBy(() -> tool.execute(mapper.createObjectNode().put("title", "")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> tool.execute(mapper.createObjectNode().put("title", "标题")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("content");
    }

    @Test
    void rejectsMalformedTableAndListArguments() throws Exception {
        var table = mapper.createObjectNode().put("title", "表");
        table.putObject("table").put("headers", "bad").putArray("rows");
        assertThatThrownBy(() -> tool.execute(table))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("table");
        var list = mapper.createObjectNode().put("title", "列").put("list", "bad");
        assertThatThrownBy(() -> tool.execute(list))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("list");
    }
}
