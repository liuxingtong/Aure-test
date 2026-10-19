package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PlanToolTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PlanTool tool = new PlanTool();

    @Test
    void rendersStableSectionsWithDefaultsAndSingleTrailingNewline() throws Exception {
        var result = tool.execute(mapper.readTree("{\"goal\":\"发布应用\"}"));

        assertThat(result)
                .contains("# 执行计划：发布应用", "## 目标与边界", "## 技术栈", "## 实施阶段", "## 验收标准")
                .endsWith("\n");
        assertThat(result).doesNotEndWith("\n\n");
    }

    @Test
    void rendersCustomPhasesAndAcceptanceArrays() throws Exception {
        var result = tool.execute(mapper.readTree("""
                {"goal":"交付功能","constraints":"不改数据库","technology":"Java 17",
                 "phases":["设计","实现"],"acceptance":["测试通过","文档更新"]}
                """));

        assertThat(result).contains("- 约束：不改数据库", "## 技术栈\n- Java 17", "### 阶段 1：设计", "### 阶段 2：实现", "- [ ] 测试通过", "- [ ] 文档更新");
    }

    @Test
    void rejectsBlankGoal() throws Exception {
        assertThatThrownBy(() -> tool.execute(mapper.readTree("{\"goal\":\"  \"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("goal");
    }

    @Test
    void fallsBackWhenOptionalArraysAreEmpty() throws Exception {
        var result = tool.execute(mapper.readTree("{\"goal\":\"目标\",\"phases\":[],\"acceptance\":[]}"));

        assertThat(result).contains("### 阶段 1：澄清与设计", "- [ ] 功能符合目标");
    }
}
