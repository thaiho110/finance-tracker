-- ADR-019: monthly budgets. A row sets a budget from one month on; the budget for month M is the newest row
-- with effective_from <= M. Past months keep the budget they had, so history stays correct.
-- category_id NULL is the optional total budget; amount 0 means "no budget from this month on".

CREATE TABLE budgets (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id    UUID          NULL REFERENCES categories(id) ON DELETE CASCADE,
    amount         DECIMAL(14, 2) NOT NULL CHECK (amount >= 0),
    effective_from DATE          NOT NULL CHECK (EXTRACT(DAY FROM effective_from) = 1),
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_budgets_user_category_month ON budgets (user_id, category_id, effective_from) NULLS NOT DISTINCT;
CREATE INDEX idx_budgets_user ON budgets (user_id, effective_from);

-- Budgets are a per-user feature (ADR-018): on by default, the super admin can switch it off for one user.
INSERT INTO role_permissions (role_code, permission) VALUES
    ('USER',  'BUDGETS'),
    ('ADMIN', 'BUDGETS');
