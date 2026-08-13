package com.corpusai.comparison_spring_ai.springai.chat;

import com.corpusai.comparison_spring_ai.chat.ChatMessage;
import com.corpusai.comparison_spring_ai.chat.ChatMessageRepository;
import com.corpusai.comparison_spring_ai.chat.MessageRole;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class PersistentChatMemoryRepository implements ChatMemoryRepository {

    // 20 turns, per obligation 9. MessageWindowChatMemory re-trims to this on every add() regardless
    // of how much history findByConversationId returns, but bounding the query keeps each turn's read
    // O(20) instead of O(full transcript) - this implementation hits the repository on every call
    // rather than caching in memory (see NOTES.md: no ChatMemoryRegistry-equivalent is needed, since
    // MessageWindowChatMemory here is stateless/repository-backed, unlike langchain4j's).
    public static final int MAX_MESSAGES = 20;

    private final ChatMessageRepository chatMessageRepository;

    public PersistentChatMemoryRepository(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @Override
    public List<String> findConversationIds() {
        // Not called by MessageWindowChatMemory/MessageChatMemoryAdvisor, and no obligation
        // requires listing sessions (no admin CRUD in scope).
        throw new UnsupportedOperationException("Sessions are addressed by id, never listed");
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        UUID sessionId = UUID.fromString(conversationId);
        return chatMessageRepository.findLastNBySessionIdOrderByCreatedAtAsc(sessionId, MAX_MESSAGES).stream()
                .map(PersistentChatMemoryRepository::toSpringAiMessage)
                .toList();
    }

    // MessageWindowChatMemory.add() always passes the full sliding window here, not just the new
    // message - persisting that verbatim would re-insert history already in comparison_chat_messages.
    // Eviction only ever trims from the front (verified against 1.1.8 bytecode), so the last element
    // is always exactly the message just added; saving only that keeps the table append-only.
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        UUID sessionId = UUID.fromString(conversationId);
        Message last = messages.get(messages.size() - 1);
        switch (last.getMessageType()) {
            case USER -> chatMessageRepository.save(sessionId, MessageRole.USER, last.getText());
            case ASSISTANT -> chatMessageRepository.save(sessionId, MessageRole.ASSISTANT, last.getText());
            // MessageChatMemoryAdvisor never routes SYSTEM or TOOL messages through add() in this
            // app's flow (verified against its before()/after() bytecode), and the schema's CHECK
            // constraint only allows USER/ASSISTANT anyway.
            default -> { }
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        chatMessageRepository.deleteAllBySessionId(UUID.fromString(conversationId));
    }

    private static Message toSpringAiMessage(ChatMessage message) {
        return message.role() == MessageRole.USER
                ? new UserMessage(message.content())
                : new AssistantMessage(message.content());
    }
}