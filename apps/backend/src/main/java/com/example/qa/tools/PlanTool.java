package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlanTool implements AiTool {
    @Override public String name() { return "create_plan"; }
    @Override public String description() { return "根据目标生成可执行的分阶段计划，不写入文件"; }
    @Override public String execute(JsonNode arguments) {
        var goal = arguments.path("goal").asText("").trim();
        if (goal.isBlank()) throw new IllegalArgumentException("goal 不能为空");
        var constraints = text(arguments, "constraints", "无特别约束");
        var stack = text(arguments, "technology", "待确定");
        var phases = values(arguments.path("phases"), List.of("澄清与设计", "实现", "验证与交付"));
        var acceptance = values(arguments.path("acceptance"), List.of("功能符合目标", "异常路径已验证", "文档与变更记录已更新"));
        var output = new StringBuilder()
                .append("# 执行计划：").append(goal).append("\n\n")
                .append("## 目标与边界\n")
                .append("- 目标：").append(goal).append("\n")
                .append("- 约束：").append(constraints).append("\n\n")
                .append("## 技术栈\n- ").append(stack).append("\n\n")
                .append("## 实施阶段\n");
        for (var i = 0; i < phases.size(); i++) {
            output.append("### 阶段 ").append(i + 1).append("：").append(phases.get(i)).append("\n")
                    .append("- 明确阶段目标与交付物\n- 为关键逻辑补充测试\n\n");
        }
        output.append("## 验收标准\n");
        acceptance.forEach(item -> output.append("- [ ] ").append(item).append("\n"));
        return output.toString().replaceFirst("\\n+$", "") + "\n";
    }

    private static String text(JsonNode arguments, String field, String fallback) {
        var value = arguments.path(field);
        return value.isTextual() && !value.asText().trim().isBlank() ? value.asText().trim() : fallback;
    }

    private static List<String> values(JsonNode node, List<String> fallback) {
        if (node.isArray()) {
            var result = new ArrayList<String>();
            node.forEach(item -> {
                if (item.isTextual() && !item.asText().trim().isBlank()) result.add(item.asText().trim());
            });
            if (!result.isEmpty()) return result;
        } else if (node.isTextual() && !node.asText().trim().isBlank()) {
            return List.of(node.asText().trim());
        }
        return fallback;
    }
}
