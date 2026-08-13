package com.corpusai.comparison_spring_ai.chat;

public sealed interface ChatStreamEvent permits ChatStreamEvent.Token, ChatStreamEvent.Done {

    record Token(String content) implements ChatStreamEvent {
    }

    record Done(Integer inputTokens, Integer outputTokens) implements ChatStreamEvent {
    }
}