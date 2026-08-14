package com.corpusai.comparison_spring_ai.springai.chat;

import com.corpusai.comparison_spring_ai.chat.ChatStreamEvent;
import com.corpusai.comparison_spring_ai.chat.ModelProvider;
import com.corpusai.comparison_spring_ai.chat.SystemPromptBuilder;
import com.corpusai.comparison_spring_ai.springai.rag.RagPipelineFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Component
public class ChatAssistant {

    private final ChatClient openAiChatClient;
    private final ChatClient anthropicChatClient;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final RagPipelineFactory ragPipelineFactory;
    private final SystemPromptBuilder systemPromptBuilder;

    public ChatAssistant(@Qualifier("openAiChatClientBuilder") ChatClient.Builder openAiChatClientBuilder,
                          @Qualifier("anthropicChatClientBuilder") ChatClient.Builder anthropicChatClientBuilder,
                          MessageChatMemoryAdvisor messageChatMemoryAdvisor,
                          RagPipelineFactory ragPipelineFactory,
                          SystemPromptBuilder systemPromptBuilder) {
        this.openAiChatClient = openAiChatClientBuilder.build();
        this.anthropicChatClient = anthropicChatClientBuilder.build();
        this.messageChatMemoryAdvisor = messageChatMemoryAdvisor;
        this.ragPipelineFactory = ragPipelineFactory;
        this.systemPromptBuilder = systemPromptBuilder;
    }

    public Flux<ChatStreamEvent> stream(UUID sessionId, String subjectId, String lang, ModelProvider provider, String userMessage) {
        String systemPrompt = systemPromptBuilder.build(subjectId, lang);
        ChatClient chatClient = provider == ModelProvider.ANTHROPIC ? anthropicChatClient : openAiChatClient;

        ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .advisors(messageChatMemoryAdvisor, ragPipelineFactory.forSubject(subjectId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId.toString()));

        // AnthropicChatOptions has no streamUsage-equivalent builder method (checked against the
        // 1.1.8 jar): a live stream confirmed Anthropic attaches real usage to its finish-reason
        // chunk unconditionally, so nothing extra is needed there.
        if (provider == ModelProvider.OPENAI) {
            request = request.options(OpenAiChatOptions.builder().streamUsage(true).build());
        }

        return request.stream()
                .chatResponse()
                .mapNotNull(this::toStreamEvent);
    }

    // finishReason, not usage, is what actually marks the terminal chunk - verified live against
    // both providers. OpenAI sets it to "STOP" and Anthropic to "end_turn" on exactly one chunk,
    // which is also the one carrying real usage; every other chunk (including a trailing
    // post-finish chunk both providers send, which repeats the same real usage with finishReason
    // back to null) has finishReason null/empty. An earlier version kept every chunk's non-null
    // Usage (OpenAI zero-fills it) for detection, which also broke on Anthropic: its very first
    // chunk already carries real prompt-token usage before any content, well before finishing.
    private ChatStreamEvent toStreamEvent(ChatResponse response) {
        String text = response.getResult() != null ? response.getResult().getOutput().getText() : null;
        if (text != null && !text.isEmpty()) {
            return new ChatStreamEvent.Token(text);
        }
        String finishReason = response.getResult() != null ? response.getResult().getMetadata().getFinishReason() : null;
        if (finishReason == null || finishReason.isEmpty()) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        return new ChatStreamEvent.Done(usage.getPromptTokens(), usage.getCompletionTokens());
    }
}