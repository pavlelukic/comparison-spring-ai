package com.corpusai.comparison_spring_ai.springai.chat;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatClientConfig {

    // With two ChatModel beans on the classpath, Spring AI's own autoconfigured ChatClient.Builder
    // can no longer resolve a single ChatModel and backs off, so both builders are declared
    // explicitly here. OpenAI is @Primary so unqualified injection (e.g. RAG query compression)
    // defaults to it.
    @Bean
    @Primary
    public ChatClient.Builder openAiChatClientBuilder(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel);
    }

    @Bean
    public ChatClient.Builder anthropicChatClientBuilder(AnthropicChatModel anthropicChatModel) {
        return ChatClient.builder(anthropicChatModel);
    }
}