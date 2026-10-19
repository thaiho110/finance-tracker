-- ADR-016 deploy hardening: hashed device API keys.
-- A device client presents its key in the X-Api-Key header; the backend authenticates the request as the owning
-- user (data written by the device belongs to this user). Only the SHA-256 hash of the key is stored, so a leak of
-- the table can't be replayed; the raw key is shown once at creation. Keys are created and revoked by an admin.

CREATE TABLE device_api_keys (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label        VARCHAR(100) NOT NULL,
    key_hash     VARCHAR(64)  NOT NULL UNIQUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by   UUID         REFERENCES users(id) ON DELETE SET NULL,
    last_used_at TIMESTAMP,
    revoked_at   TIMESTAMP
);

CREATE INDEX idx_device_api_keys_user ON device_api_keys(user_id);
