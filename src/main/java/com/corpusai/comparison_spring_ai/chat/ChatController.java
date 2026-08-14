package com.corpusai.comparison_spring_ai.chat;

import com.corpusai.comparison_spring_ai.chat.dto.ChatChunkResponse;
import com.corpusai.comparison_spring_ai.chat.dto.ChatDoneResponse;
import com.corpusai.comparison_spring_ai.chat.dto.ChatSessionResponse;
import com.corpusai.comparison_spring_ai.chat.dto.CreateChatSessionRequest;
import com.corpusai.comparison_spring_ai.chat.dto.SendMessageRequest;
import com.corpusai.comparison_spring_ai.springai.chat.ChatAssistant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private static final String DEFAULT_LANG = "en";
    private static final ModelProvider DEFAULT_PROVIDER = ModelProvider.OPENAI;
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatAssistant chatAssistant;

    public ChatController(ChatSessionRepository chatSessionRepository, ChatAssistant chatAssistant) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatAssistant = chatAssistant;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse createSession(@RequestBody CreateChatSessionRequest request) {
        String lang = request.lang() != null ? request.lang() : DEFAULT_LANG;
        ModelProvider provider = request.provider() != null ? request.provider() : DEFAULT_PROVIDER;
        ChatSession session = chatSessionRepository.create(request.subjectId(), lang, provider);
        return new ChatSessionResponse(session.id(), session.subjectId(), session.lang(), session.provider(), session.createdAt());
    }

    @PostMapping("/{sessionId}/messages")
    public SseEmitter sendMessage(@PathVariable UUID sessionId, @RequestBody SendMessageRequest request) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown chat session: " + sessionId));
        chatSessionRepository.touch(sessionId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Instant startedAt = Instant.now();

        chatAssistant.stream(sessionId, session.subjectId(), session.lang(), session.provider(), request.message())
                .subscribe(
                        event -> sendEvent(emitter, sessionId, startedAt, event),
                        emitter::completeWithError,
                        emitter::complete);

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, UUID sessionId, Instant startedAt, ChatStreamEvent event) {
        try {
            switch (event) {
                case ChatStreamEvent.Token token -> emitter.send(SseEmitter.event()
                        .name("token")
                        .data(new ChatChunkResponse(token.content())));
                case ChatStreamEvent.Done done -> {
                    long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
                    log.info("Chat usage - session: '{}', input tokens: {}, output tokens: {}, latency: {}ms",
                            sessionId, done.inputTokens(), done.outputTokens(), latencyMs);
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(new ChatDoneResponse(done.inputTokens(), done.outputTokens(), latencyMs)));
                }
            }
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}