package com.corpusai.comparison_spring_ai.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(UUID id, String subjectId, String lang, Instant createdAt) {
}