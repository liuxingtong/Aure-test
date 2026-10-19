package com.example.qa.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(boolean enabled, String provider, String baseUrl, String apiKey, String model, Duration timeout) {}
