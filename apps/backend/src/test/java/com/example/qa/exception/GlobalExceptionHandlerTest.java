package com.example.qa.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.qa.ai.ProviderHttpException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsInvalidApiKeyToActionableAuthError() {
        var response = handler.provider(new ProviderHttpException(401, "API Key 无效或没有访问权限，请在账户设置中检查。"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().error().code()).isEqualTo("AI_AUTH_ERROR");
        assertThat(response.getBody().error().message()).contains("API Key");
        assertThat(response.getBody().error().retryable()).isFalse();
    }

    @Test
    void mapsRateLimitAsRetryable() {
        var response = handler.provider(new ProviderHttpException(429, "AI 服务请求过于频繁，请稍后重试。"));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getBody().error().code()).isEqualTo("AI_RATE_LIMITED");
        assertThat(response.getBody().error().retryable()).isTrue();
    }

    @Test
    void mapsConnectionFailureSeparately() {
        var response = handler.connection(new ResourceAccessException("connection refused"));

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().error().code()).isEqualTo("AI_CONNECTION_ERROR");
        assertThat(response.getBody().error().retryable()).isTrue();
    }
}
