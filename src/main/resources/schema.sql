CREATE TABLE IF NOT EXISTS comparison_chat_sessions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id VARCHAR(255) NOT NULL,
    lang       VARCHAR(5) NOT NULL DEFAULT 'en',
    provider   VARCHAR(20) NOT NULL DEFAULT 'OPENAI',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
    );

CREATE TABLE IF NOT EXISTS comparison_chat_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES comparison_chat_sessions(id) ON DELETE CASCADE,
    role       VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content    TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
    );