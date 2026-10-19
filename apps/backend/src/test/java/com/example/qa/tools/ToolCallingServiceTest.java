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
            assertThat(history).anyMatch(message -> message.role().equals("user") && message.content().contains("4"));
            return "最终答案：4";
        };
        var service = new ToolCallingService(client, TestToolRegistry.create(), new ObjectMapper());

        assertThat(service.answerWithTools(List.of(), "算一下", null).answer()).isEqualTo("最终答案：4");
        assertThat(calls).hasValue(2);
    }

    @Test
    void searchKeywordsDoNotBypassTheModel() {
        var calls = new AtomicInteger();
        AiClient client = (history, question) -> {
            calls.incrementAndGet();
            return "正常回答";
        };
        var service = new ToolCallingService(client, TestToolRegistry.create(), new ObjectMapper());

        assertThat(service.answer(List.of(), "现在可以联网搜索 AI 新闻吗")).isEqualTo("正常回答");
        assertThat(calls).hasValue(1);
    }

    @Test
    void parsesToolCallEmbeddedInNaturalLanguageAndHidesProtocolJson() {
        var calls = new AtomicInteger();
        AiClient client = (history, question) -> {
            if (calls.getAndIncrement() == 0) {
                return "我来搜索一下：{\"tool_calls\":[{\"name\":\"search_latest_news\",\"arguments\":{\"query\":\"AI\"}}]}";
            }
            assertThat(history).anyMatch(message -> message.role().equals("user") && message.content().contains("search_latest_news"));
            return "下面是新闻链接，请查收。";
        };
        var service = new ToolCallingService(client, TestToolRegistry.create(), new ObjectMapper());

        assertThat(service.answer(List.of(), "热门 AI 新闻")).isEqualTo("我来搜索一下：\n\n下面是新闻链接，请查收。");
        assertThat(calls).hasValue(2);
    }

    @Test
    void forcesFinalAnswerAfterMaxCallsAreExhausted() {
        // 永远返回 tool call，验证不会无限循环——用尽 MAX_CALLS 后应被强制要答案
        AiClient client = (history, question) -> "{\"tool_calls\":[{\"name\":\"calculate\",\"arguments\":{\"expression\":\"1+1\"}}]}";
        var service = new ToolCallingService(client, TestToolRegistry.create(), new ObjectMapper());

        // 第 7 次调用（6 次工具 + 1 次强制终答）应返回非空答案
        var result = service.answerWithTools(List.of(), "算 1+1", null);

        assertThat(result.answer()).isNotEmpty();
        assertThat(result.toolCalls()).hasSize(ToolCallingService.MAX_CALLS);
    }

    @Test
    void injectsOnlyAuthorizedDocumentIntoReadDocument() throws Exception {
        var calls = new AtomicInteger();
        AiClient client = (history, question) -> {
            if (calls.getAndIncrement() == 0) {
                return "{\"tool_calls\":[{\"name\":\"read_document\",\"arguments\":{\"filename\":\"notes\"}}]}";
            }
            assertThat(history).anyMatch(message -> message.role().equals("user") && message.content().contains("# Notes"));
            return "文档内容如上。";
        };
        var service = new ToolCallingService(client, TestToolRegistry.create(), new ObjectMapper());
        var document = new ObjectMapper().readTree("{\"name\":\"notes.md\",\"extension\":\"md\",\"content\":\"# Notes\"}");

        assertThat(service.answerWithTools(List.of(), "读一下文档", null, document).answer()).isEqualTo("文档内容如上。");
        assertThat(calls).hasValue(2);
    }

    @Test
    void preservesLeadInNaturalLanguageBeforeFirstToolCall() {
        var calls = new AtomicInteger();
        AiClient client = (history, question) -> {
            if (calls.getAndIncrement() == 0) {
                return "我先算一下，稍等。\n{\"tool_calls\":[{\"name\":\"calculate\",\"arguments\":{\"expression\":\"2+2\"}}]}";
            }
            return "最终结果：4";
        };
        var service = new ToolCallingService(client, TestToolRegistry.create(), new ObjectMapper());

        var result = service.answerWithTools(List.of(), "算一下", null);

        assertThat(result.answer()).contains("我先算一下").contains("最终结果：4");
    }
}
