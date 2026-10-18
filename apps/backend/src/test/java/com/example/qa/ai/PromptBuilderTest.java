package com.example.qa.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.qa.model.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {
    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void placesSystemInstructionHistoryAndQuestionInOrder() {
        var messages = builder.build(
                List.of(new ChatMessage("m1", "user", "上一问", "2026-01-01T00:00:00Z")),
                "  当前问题  ");

        assertThat(messages).extracting(ChatMessage::role)
                .containsExactly("system", "user", "user");
        assertThat(messages.get(0).content()).contains("Markdown");
        assertThat(messages.get(2).content()).isEqualTo("当前问题");
    }
}
