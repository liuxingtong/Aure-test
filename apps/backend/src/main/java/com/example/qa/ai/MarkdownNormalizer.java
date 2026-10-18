package com.example.qa.ai;

public final class MarkdownNormalizer {
    private MarkdownNormalizer() {}

    public static String normalize(String content) {
        if (content == null || content.isBlank()) return "暂无回答";
        return content.replace("\r\n", "\n").trim();
    }
}
