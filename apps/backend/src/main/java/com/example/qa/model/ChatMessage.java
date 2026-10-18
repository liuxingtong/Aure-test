package com.example.qa.model;

import jakarta.validation.constraints.NotBlank;

public record ChatMessage(@NotBlank String id, @NotBlank String role, @NotBlank String content, @NotBlank String createdAt) {}
