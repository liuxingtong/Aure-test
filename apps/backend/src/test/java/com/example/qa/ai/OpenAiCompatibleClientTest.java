package com.example.qa.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.qa.model.ChatMessage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleClientTest {
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void parsesOpenAiCompatibleMarkdownResponse() {
        server.createContext("/chat/completions", exchange -> respond(exchange,
                "{\"choices\":[{\"message\":{\"content\":\"# 结果\\r\\n\\r\\n内容\"}}]}"));
        var client = new OpenAiCompatibleClient(RestClient.builder(), properties());

        var answer = client.chat(List.of(new ChatMessage("m1", "user", "历史", "now")), "问题");

        assertThat(answer).isEqualTo("# 结果\n\n内容");
    }

    @Test
    void rejectsEmptyChoices() {
        server.createContext("/chat/completions", exchange -> respond(exchange, "{\"choices\":[]}"));
        var client = new OpenAiCompatibleClient(RestClient.builder(), properties());

        assertThatThrownBy(() -> client.chat(List.of(), "问题"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI 返回结果为空");
    }

    @Test
    void appliesConfiguredReadTimeout() {
        server.createContext("/chat/completions", exchange -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, "{\"choices\":[]}");
        });
        var properties = new AiProperties(true, "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "test-model", Duration.ofMillis(50));
        var client = new OpenAiCompatibleClient(RestClient.builder(), properties);

        assertThatThrownBy(() -> client.chat(List.of(), "问题"))
                .isInstanceOf(org.springframework.web.client.RestClientException.class);
    }

    private AiProperties properties() {
        return new AiProperties(true, "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "test-model", Duration.ofSeconds(2));
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
