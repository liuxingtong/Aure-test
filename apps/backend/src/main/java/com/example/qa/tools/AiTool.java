package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;

public interface AiTool {
    String name();

    String description();

    String execute(JsonNode arguments);
}
