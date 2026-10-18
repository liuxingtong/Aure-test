package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolCall(String name, JsonNode arguments) {}
