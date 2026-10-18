package com.example.qa.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownNormalizerTest {
    @Test
    void normalizesLineEndingsAndWhitespace() {
        assertThat(MarkdownNormalizer.normalize("  # 标题\r\n\r\n正文  "))
                .isEqualTo("# 标题\n\n正文");
    }

    @Test
    void suppliesFallbackForBlankAnswer() {
        assertThat(MarkdownNormalizer.normalize(" \n\t")).isEqualTo("暂无回答");
        assertThat(MarkdownNormalizer.normalize(null)).isEqualTo("暂无回答");
    }
}
