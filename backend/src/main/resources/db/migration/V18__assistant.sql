-- ADR-024: the AI assistant. Per-user conversation threads and messages. A proposed mutating action rides in a
-- message's tool_calls (a confirm-card the user approves); stored as text for now. The provider adapter is
-- behind a code interface (ai.ChatModel) and starts as a stub, so nothing here is provider-specific.

CREATE TABLE assistant_threads (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_assistant_threads_user ON assistant_threads (user_id, created_at DESC);

CREATE TABLE assistant_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id  UUID        NOT NULL REFERENCES assistant_threads(id) ON DELETE CASCADE,
    role       VARCHAR(20) NOT NULL,     -- USER | ASSISTANT | TOOL
    content    TEXT,
    tool_calls TEXT,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_assistant_messages_thread ON assistant_messages (thread_id, created_at);

-- ASSISTANT_USE was added and granted to USER/ADMIN in V15 (ADR-020 §4); the ASSISTANT module gates it.
