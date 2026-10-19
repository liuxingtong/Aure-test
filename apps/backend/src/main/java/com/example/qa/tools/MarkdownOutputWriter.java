package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 决定 MarkdownDocumentTool 的输出文件路径并真正落盘。
 * 默认写到 <documentsDir>/yunhe/YYYY-MM-DD-{slug}.md。
 * 解析工作目录可由配置 ai.markdown.output-dir 覆盖。
 */
@Component
public class MarkdownOutputWriter {
    private static final Pattern SLUG = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path outputDir;

    @Autowired
    public MarkdownOutputWriter(@Value("${ai.markdown.output-dir:}") String configured) {
        Path resolved;
        if (configured == null || configured.isBlank()) {
            resolved = defaultDocumentsDir();
        } else {
            resolved = Path.of(configured);
        }
        this.outputDir = resolved.resolve("yunhe");
    }
    /** 暴露给测试用；与生产构造器一致，真实输出目录是 `<documentsDir>/yunhe`。 */
    MarkdownOutputWriter(Path documentsDir) {
        this.outputDir = documentsDir.resolve("yunhe");
    }

    public Result write(String title, String markdown, JsonNode arguments) {
        try {
            Files.createDirectories(outputDir);
            var name = sanitize(title) + "-" + LocalDate.now().format(DATE) + ".md";
            var unique = unique(outputDir, name);
            Files.writeString(unique, markdown, StandardCharsets.UTF_8);
            return new Result(unique, markdown);
        } catch (Exception exception) {
            throw new ToolException("TOOL_EXECUTION_FAILED", "无法写入 Markdown 文件：" + exception.getMessage(), false);
        }
    }

    private static Path defaultDocumentsDir() {
        var home = System.getProperty("user.home");
        var explicit = System.getenv("YUNHE_DOCS_DIR");
        if (explicit != null && !explicit.isBlank()) return Path.of(explicit);
        if (home == null || home.isBlank()) return Path.of("yunhe-docs");
        // macOS / Windows / Linux 都用 ~/Documents
        return Path.of(home, "Documents");
    }

    private static Path unique(Path dir, String name) {
        var candidate = dir.resolve(name);
        if (!Files.exists(candidate)) return candidate;
        var stem = name.replaceFirst("\\.md$", "");
        for (int i = 2; i < 1000; i++) {
            var next = dir.resolve(stem + "-" + i + ".md");
            if (!Files.exists(next)) return next;
        }
        throw new IllegalStateException("无法生成唯一文件名");
    }

    private static String sanitize(String title) {
        var collapsed = SLUG.matcher(title.trim()).replaceAll("-");
        if (collapsed.length() > 60) collapsed = collapsed.substring(0, 60);
        var trimmed = collapsed.replaceAll("(^-+)|(-+$)", "");
        return trimmed.isEmpty() ? "未命名" : trimmed;
    }

    public record Result(Path path, String content) {}
}
