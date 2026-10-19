package com.example.qa.ai;

/**
 * Provider 返回的非 2xx 响应。单独建模是为了让 controller 能给用户
 * “API Key 无效”“模型不存在”“限流”这类可操作提示，而不是统一显示服务不可用。
 */
public class ProviderHttpException extends RuntimeException {
    private final int status;

    public ProviderHttpException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public boolean retryable() {
        return status == 429 || status >= 500;
    }
}
