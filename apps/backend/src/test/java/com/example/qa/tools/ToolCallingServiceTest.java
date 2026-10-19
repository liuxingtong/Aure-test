package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.qa.ai.AiClient;
import com.example.qa.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ToolCallingServiceTest {
    @Test
    void executesToolAndReturnsToolResultWithoutLeakingProtocol() {
        var calls = new AtomicInteger();
        AiClient client = (history, question) -> {
            if (calls.getAndIncrement() == 0) {
                return "{\"tool_calls\":[{\"name\":\"calculate\",\"arguments\":{\"expression\":\"2+2\"}}]}";
            }
            assertThat(history).anyMatch(message -> message.role().equals("tool") && message.content().contains("4"));
            return "最终答案：4";
        };
        var service = new ToolCallingService(client, new ToolRegistry(new CalculateTool(), new SearchLatestNewsTool()), new ObjectMapper());

        assertThat(service.answerWithTools(List.of(), "算一下", null).answer()).isEqualTo("最终答案：4");
        assertThat(calls).hasValue(2);
    }

    @Test
    void searchIntentExecutesSearchToolWithoutDependingOnModelToolCalling() {
        var service = new ToolCallingService((history, question) -> "模型不应先回答", new ToolRegistry(new CalculateTool(), new SearchLatestNewsTool()), new ObjectMapper());

        assertThat(service.answer(List.of(), "现在可以联网搜索 AI 新闻吗"))
                .contains("Google News", "Bing News");
    }

    @Test
    void parsesToolCallEmbeddedInNaturalLanguageAndHidesProtocolJson() {
        AiClient client = (history, question) -> "我来搜索一下：{\"tool_calls\":[{\"name\":\"search_latest_news\",\"arguments\":{\"query\":\"AI\"}}]}";
        var service = new ToolCallingService(client, new ToolRegistry(new CalculateTool(), new SearchLatestNewsTool()), new ObjectMapper());

        assertThat(service.answer(List.of(), "热门 AI 新闻")).contains("Google News").doesNotContain("tool_calls");
    }
}
