package com.corpusai.comparison_spring_ai.chat;

import com.corpusai.comparison_spring_ai.chat.dto.ChatSessionResponse;
import com.corpusai.comparison_spring_ai.chat.dto.CreateChatSessionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private static final String DEFAULT_LANG = "en";

    private final ChatSessionRepository chatSessionRepository;

    public ChatController(ChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = chatSessionRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse createSession(@RequestBody CreateChatSessionRequest request) {
        String lang = request.lang() != null ? request.lang() : DEFAULT_LANG;
        ChatSession session = chatSessionRepository.create(request.subjectId(), lang);
        return new ChatSessionResponse(session.id(), session.subjectId(), session.lang(), session.createdAt());
    }
}