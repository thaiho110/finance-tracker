-- ADR-020 §4: user-chosen modules. A module groups permissions a user can turn on or off for themselves; no
-- row means the module is on. Turning one off subtracts its permissions from the user's effective set (it never
-- grants). Administration/monitoring permissions belong to no module. The module→permission mapping lives in
-- code (access.model.Module); this table only stores each user's on/off choice.
--
-- Numbering note: ADR-020 wrote this as V10; V10-V12 are superseded (V13/V14 were taken by ADR-021/022), so it
-- lands at V15. The module system is ADR-020 §4 only; statements/tabs/OCR-refactor remain unbuilt.

CREATE TABLE user_module_preferences (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    module     VARCHAR(30) NOT NULL,     -- SPENDING | ASSISTANT | INTEGRATIONS | MEALS
    enabled    BOOLEAN     NOT NULL,
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, module)
);
CREATE INDEX idx_user_module_preferences_user ON user_module_preferences (user_id);

-- New FEATURE permissions the modules gate (ADR-023/024/025). Granted to USER and ADMIN so the modules are
-- available by default; the per-feature controllers (built later) check these, and a user who turns the module
-- off loses them via effective permissions.
INSERT INTO role_permissions (role_code, permission) VALUES
    ('USER',  'ASSISTANT_USE'),
    ('ADMIN', 'ASSISTANT_USE'),
    ('USER',  'INTEGRATIONS_MANAGE'),
    ('ADMIN', 'INTEGRATIONS_MANAGE'),
    ('USER',  'MEALS_CALL'),
    ('ADMIN', 'MEALS_CALL'),
    ('USER',  'MEALS_RESPOND'),
    ('ADMIN', 'MEALS_RESPOND');
