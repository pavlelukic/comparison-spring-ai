package com.corpusai.comparison_spring_ai.chat.dto;

import com.corpusai.comparison_spring_ai.chat.ModelProvider;

public record CreateChatSessionRequest(String subjectId, String lang, ModelProvider provider) {
}