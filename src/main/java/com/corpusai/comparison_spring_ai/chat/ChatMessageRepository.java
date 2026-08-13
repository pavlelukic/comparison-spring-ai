package com.corpusai.comparison_spring_ai.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Repository
public class ChatMessageRepository {

    private static final RowMapper<ChatMessage> ROW_MAPPER = (rs, rowNum) -> new ChatMessage(
            rs.getObject("id", UUID.class),
            rs.getObject("session_id", UUID.class),
            MessageRole.valueOf(rs.getString("role")),
            rs.getString("content"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatMessage save(UUID sessionId, MessageRole role, String content) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO comparison_chat_messages (session_id, role, content)
                VALUES (?, ?, ?)
                RETURNING id, session_id, role, content, created_at
                """, ROW_MAPPER, sessionId, role.name(), content);
    }

    public List<ChatMessage> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId) {
        return jdbcTemplate.query("""
                SELECT id, session_id, role, content, created_at
                FROM comparison_chat_messages
                WHERE session_id = ?
                ORDER BY created_at ASC
                """, ROW_MAPPER, sessionId);
    }
    
    public List<ChatMessage> findLastNBySessionIdOrderByCreatedAtAsc(UUID sessionId, int limit) {
        List<ChatMessage> lastNDescending = jdbcTemplate.query("""
                SELECT id, session_id, role, content, created_at
                FROM comparison_chat_messages
                WHERE session_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """, ROW_MAPPER, sessionId, limit);
        Collections.reverse(lastNDescending);
        return lastNDescending;
    }
}