package com.example.qa.ai;

/**
 * 一轮 AI 调用的结果：final content + 可选 thinking（推理痕迹）。
 */
public record ChatResult(String content, String thinking) {}
