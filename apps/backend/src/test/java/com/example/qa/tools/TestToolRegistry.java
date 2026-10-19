package com.example.qa.tools;

import java.nio.file.Path;

/** 测试用工具注册表：生产构造器要求注入所有工具 bean。 */
final class TestToolRegistry {
    private TestToolRegistry() {}

    static ToolRegistry create() {
        return create(Path.of(System.getProperty("java.io.tmpdir"), "yunhe-test-" + System.nanoTime()));
    }

    static ToolRegistry create(Path outputDir) {
        return new ToolRegistry(
                new CalculateTool(),
                new SearchLatestNewsTool(),
                new PlanTool(),
                new MarkdownDocumentTool(new MarkdownOutputWriter(outputDir)),
                new ReadDocumentTool());
    }
}
