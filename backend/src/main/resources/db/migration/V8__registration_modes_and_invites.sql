-- ADR-016 deploy hardening: registration modes and single-use invites.
-- app_settings holds application-wide settings as strings; registration_mode is OPEN | INVITE_ONLY | CLOSED.
-- Production defaults to INVITE_ONLY; dev forces OPEN via app.registration.forced-mode.

CREATE TABLE app_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO app_settings (setting_key, setting_value) VALUES ('registration_mode', 'INVITE_ONLY');

-- Single-use invites. Only the SHA-256 hash of the token is stored; the raw token is shown once at creation.
CREATE TABLE registration_invites (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_by UUID        REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,
    used_by    UUID        REFERENCES users(id) ON DELETE SET NULL
);
