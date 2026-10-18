-- ADR-018: role table, permissions per domain, per-user features, audit log.
-- Permission names match com.financetracker.access.model.Permission.

CREATE TABLE roles (
    code        VARCHAR(20)  PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    rank        INT          NOT NULL,
    description VARCHAR(255),
    built_in    BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO roles (code, name, rank, description) VALUES
    ('USER',        'User',        10,  'Tracks their own finances'),
    ('ADMIN',       'Admin',       50,  'Runs day-to-day operations: accounts, audit log, monitoring'),
    ('SUPER_ADMIN', 'Super admin', 100, 'Owns the application and always has every permission');

CREATE TABLE role_permissions (
    role_code  VARCHAR(20) NOT NULL REFERENCES roles(code) ON DELETE CASCADE,
    permission VARCHAR(50) NOT NULL,
    PRIMARY KEY (role_code, permission)
);

-- SUPER_ADMIN gets every permission in code, so it has no rows here.
INSERT INTO role_permissions (role_code, permission) VALUES
    ('USER',  'TRANSACTION_READ'),
    ('USER',  'TRANSACTION_WRITE'),
    ('USER',  'DASHBOARD_VIEW'),
    ('USER',  'CSV_IMPORT'),
    ('USER',  'RECEIPT_SCAN'),
    ('USER',  'CATEGORY_READ'),
    ('ADMIN', 'TRANSACTION_READ'),
    ('ADMIN', 'TRANSACTION_WRITE'),
    ('ADMIN', 'DASHBOARD_VIEW'),
    ('ADMIN', 'CSV_IMPORT'),
    ('ADMIN', 'RECEIPT_SCAN'),
    ('ADMIN', 'CATEGORY_READ'),
    ('ADMIN', 'USER_READ'),
    ('ADMIN', 'USER_SUSPEND'),
    ('ADMIN', 'AUDIT_READ'),
    ('ADMIN', 'MONITORING_VIEW');

-- Existing accounts: role codes become upper case and must exist in roles.
UPDATE users SET role = UPPER(role);
UPDATE users SET role = 'USER' WHERE role NOT IN (SELECT code FROM roles);
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT fk_users_role FOREIGN KEY (role) REFERENCES roles(code);
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP;

-- A feature switched on or off for one user, overriding what their role grants.
CREATE TABLE user_feature_overrides (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission VARCHAR(50) NOT NULL,
    enabled    BOOLEAN     NOT NULL,
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_user_feature UNIQUE (user_id, permission)
);

CREATE TABLE audit_log (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id   UUID REFERENCES users(id) ON DELETE SET NULL,
    action     VARCHAR(50)  NOT NULL,
    target     VARCHAR(255),
    details    TEXT,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_created_at ON audit_log(created_at DESC);
