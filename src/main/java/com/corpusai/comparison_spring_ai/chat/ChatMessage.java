package com.corpusai.comparison_spring_ai.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(UUID id, UUID sessionId, MessageRole role, String content, Instant createdAt) {
}