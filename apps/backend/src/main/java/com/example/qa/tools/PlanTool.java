package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class PlanTool implements AiTool {
    @Override public String name() { return "create_plan"; }
    @Override public String description() { return "根据目标生成可执行的分阶段计划，不写入文件"; }
    @Override public String execute(JsonNode arguments) {
        var goal = arguments.path("goal").asText("").trim();
        if (goal.isBlank()) throw new IllegalArgumentException("goal 不能为空");
        var constraints = arguments.path("constraints").asText("无特别约束").trim();
        var stack = arguments.path("technology").asText("待确定").trim();
        return "## 执行计划：" + goal + "\n\n### 目标与边界\n- 目标：" + goal + "\n- 约束：" + constraints + "\n- 技术栈：" + stack + "\n\n### 阶段 1：澄清与设计\n- 明确验收标准\n- 拆分任务与风险\n\n### 阶段 2：实现\n- 按优先级实现最小可用路径\n- 为关键逻辑补充测试\n\n### 阶段 3：验证与交付\n- 运行测试与构建\n- 记录已知限制和后续事项\n\n### 验收清单\n- [ ] 功能符合目标\n- [ ] 异常路径已验证\n- [ ] 文档与变更记录已更新";
    }
}
