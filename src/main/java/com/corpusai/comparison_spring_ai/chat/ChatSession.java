package com.corpusai.comparison_spring_ai.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatSession(UUID id, String subjectId, String lang, Instant createdAt, Instant updatedAt) {
}