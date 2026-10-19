package com.example.qa.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatMessage(
        @NotBlank @Size(max = 128) String id,
        @NotBlank @Pattern(regexp = "user|assistant", message = "role 只允许 user 或 assistant") String role,
        @NotBlank @Size(max = 20000) String content,
        @NotBlank @Size(max = 64) String createdAt) {}
