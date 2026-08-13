package com.corpusai.comparison_spring_ai.springai.chat;

import com.corpusai.comparison_spring_ai.chat.ChatStreamEvent;
import com.corpusai.comparison_spring_ai.chat.SystemPromptBuilder;
import com.corpusai.comparison_spring_ai.springai.rag.RagPipelineFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ChatAssistant {

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final RagPipelineFactory ragPipelineFactory;
    private final SystemPromptBuilder systemPromptBuilder;

    public ChatAssistant(ChatClient.Builder chatClientBuilder,
                          MessageChatMemoryAdvisor messageChatMemoryAdvisor,
                          RagPipelineFactory ragPipelineFactory,
                          SystemPromptBuilder systemPromptBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.messageChatMemoryAdvisor = messageChatMemoryAdvisor;
        this.ragPipelineFactory = ragPipelineFactory;
        this.systemPromptBuilder = systemPromptBuilder;
    }

    public Flux<ChatStreamEvent> stream(UUID sessionId, String subjectId, String lang, String userMessage) {
        String systemPrompt = systemPromptBuilder.build(subjectId, lang);
        AtomicBoolean doneSent = new AtomicBoolean(false);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .advisors(messageChatMemoryAdvisor, ragPipelineFactory.forSubject(subjectId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId.toString()))
                .options(OpenAiChatOptions.builder().streamUsage(true).build())
                .stream()
                .chatResponse()
                .mapNotNull(this::toStreamEvent)
                .filter(event -> !(event instanceof ChatStreamEvent.Done) || doneSent.compareAndSet(false, true));
    }

    // Every chunk carries a non-null Usage (Spring AI's OpenAI binding fills it with zeros rather
    // than leaving it null), so usage presence alone can't identify the final chunk - verified
    // against a live stream, where checking getPromptTokens() != null misclassified every content
    // chunk as the final one, and an early all-zero-usage/no-text chunk still needs excluding even
    // after switching the check to text-first. Usage only counts here once it reports real tokens.
    private ChatStreamEvent toStreamEvent(ChatResponse response) {
        String text = response.getResult() != null ? response.getResult().getOutput().getText() : null;
        if (text != null && !text.isEmpty()) {
            return new ChatStreamEvent.Token(text);
        }
        Usage usage = response.getMetadata().getUsage();
        boolean hasRealUsage = usage != null
                && ((usage.getPromptTokens() != null && usage.getPromptTokens() > 0)
                        || (usage.getCompletionTokens() != null && usage.getCompletionTokens() > 0));
        return hasRealUsage ? new ChatStreamEvent.Done(usage.getPromptTokens(), usage.getCompletionTokens()) : null;
    }
}