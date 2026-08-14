package com.corpusai.comparison_spring_ai.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ChatSessionRepository {

    private static final RowMapper<ChatSession> ROW_MAPPER = (rs, rowNum) -> new ChatSession(
            rs.getObject("id", UUID.class),
            rs.getString("subject_id"),
            rs.getString("lang"),
            ModelProvider.valueOf(rs.getString("provider")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public ChatSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatSession create(String subjectId, String lang, ModelProvider provider) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO comparison_chat_sessions (subject_id, lang, provider)
                VALUES (?, ?, ?)
                RETURNING id, subject_id, lang, provider, created_at, updated_at
                """, ROW_MAPPER, subjectId, lang, provider.name());
    }

    public Optional<ChatSession> findById(UUID id) {
        return jdbcTemplate.query("""
                SELECT id, subject_id, lang, provider, created_at, updated_at
                FROM comparison_chat_sessions
                WHERE id = ?
                """, ROW_MAPPER, id).stream().findFirst();
    }

    public void touch(UUID id) {
        jdbcTemplate.update("""
                UPDATE comparison_chat_sessions
                SET updated_at = now()
                WHERE id = ?
                """, id);
    }
}