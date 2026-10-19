package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.qa.ai.AiClient;
import com.example.qa.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolCallingServiceTest {
    @Test
    void executesToolThenSendsToolResultBackToAi() {
        AiClient client = new AiClient() {
            private int calls;
            @Override public String chat(List<ChatMessage> history, String question) {
                return calls++ == 0
                        ? "{\"tool_calls\":[{\"name\":\"calculate\",\"arguments\":{\"expression\":\"2+2\"}}]}"
                        : "最终答案：4";
            }
        };
        var service = new ToolCallingService(client, new ToolRegistry(new CalculateTool(), new SearchLatestNewsTool()), new ObjectMapper());

        assertThat(service.answer(List.of(), "算一下")).isEqualTo("最终答案：4");
    }

    @Test
    void searchIntentExecutesSearchToolWithoutDependingOnModelToolCalling() {
        var service = new ToolCallingService((history, question) -> "模型不应先回答", new ToolRegistry(new CalculateTool(), new SearchLatestNewsTool()), new ObjectMapper());

        assertThat(service.answer(List.of(), "现在可以联网搜索 AI 新闻吗"))
                .contains("Google News", "Bing News");
    }
}
