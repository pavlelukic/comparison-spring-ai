package com.corpusai.comparison_spring_ai.chat.dto;

public record ChatDoneResponse(Integer inputTokens, Integer outputTokens, long latencyMs) {
}