-- ADR-021: the notifications subsystem. Every notification is a row in the user's in-app feed; a type that
-- routes to push (see NotificationRouting) is also delivered to the user's devices and flagged pushed = true.
--
-- ADR-021's richer `data JSONB` / `channels[]` payload is reduced here to `deep_link` + `pushed`. The
-- arbitrary-payload column is deferred until a producer needs it (meal-call quick-reply actions, ADR-025),
-- when its JSON mapping is chosen deliberately.
--
-- Numbering note: V10-V12 belong to ADR-020 (feature modules / statements / tabs), still on its worktree
-- branch and not merged to main. On main V13 follows V9; if ADR-020 later merges, reconcile the order.

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(1000),
    deep_link  VARCHAR(500),
    pushed     BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at    TIMESTAMP    NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE push_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client       VARCHAR(40) NOT NULL,     -- ADR-009 X-Client-Id family: web / desktop / mobile
    transport    VARCHAR(20) NOT NULL,     -- WEB_PUSH | FCM | APNS
    token        TEXT        NOT NULL UNIQUE,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_push_tokens_user ON push_tokens (user_id);

-- No role_permissions rows: notifications are platform and always on (ADR-021 §1), gated only by being
-- signed in, which Spring Security's default authenticated() rule already enforces.
