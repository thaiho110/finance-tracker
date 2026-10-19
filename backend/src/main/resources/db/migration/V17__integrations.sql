-- ADR-023: integrations. Inbound signed webhook endpoints raise an internal event; outbound subscriptions get
-- a signed POST when a matching event fires. The internal event bus is in-process (Spring events), not a table.
-- Secrets are stored as-is because HMAC verification/signing must recompute with the same key.

CREATE TABLE webhook_endpoints (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    token         VARCHAR(64) NOT NULL UNIQUE,
    secret        VARCHAR(128) NOT NULL,
    event_type    VARCHAR(50) NOT NULL DEFAULT 'integration.trigger',
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_webhook_endpoints_owner ON webhook_endpoints (owner_user_id);

CREATE TABLE webhook_subscriptions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_url    VARCHAR(2000) NOT NULL,
    secret        VARCHAR(128) NOT NULL,
    event_types   VARCHAR(500) NOT NULL,   -- comma-separated (kept simple rather than a Postgres array)
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_webhook_subscriptions_owner ON webhook_subscriptions (owner_user_id);

CREATE TABLE webhook_deliveries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID        NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
    event_type      VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL,   -- PENDING | DELIVERED | FAILED
    attempts        INT         NOT NULL DEFAULT 0,
    response_code   INT,
    last_attempt_at TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_webhook_deliveries_subscription ON webhook_deliveries (subscription_id, created_at DESC);

-- INTEGRATIONS_MANAGE was added and granted in V15 (ADR-020 §4); the INTEGRATIONS module gates it.
