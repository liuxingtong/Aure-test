package com.example.qa.tools;

public class ToolException extends IllegalArgumentException {
    private final String code;
    private final boolean retryable;

    public ToolException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() { return code; }
    public boolean retryable() { return retryable; }
}
