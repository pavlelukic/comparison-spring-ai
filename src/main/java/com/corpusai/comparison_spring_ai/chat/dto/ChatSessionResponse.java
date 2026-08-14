package com.corpusai.comparison_spring_ai.chat.dto;

import com.corpusai.comparison_spring_ai.chat.ModelProvider;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(UUID id, String subjectId, String lang, ModelProvider provider, Instant createdAt) {
}