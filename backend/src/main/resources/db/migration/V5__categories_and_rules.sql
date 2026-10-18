-- ADR-015 (amended): categories and keyword rules owned by the system (user_id IS NULL) or by one user.
-- transactions.category (free text) is replaced by transactions.category_id. The old column and
-- category_mappings stay until V6 so this migration can be checked against real data first.

CREATE TABLE categories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    kind       VARCHAR(10)  NOT NULL DEFAULT 'EXPENSE' CHECK (kind IN ('EXPENSE', 'INCOME', 'TRANSFER')),
    color      VARCHAR(20),
    icon       VARCHAR(40),
    archived   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
-- One name per owner. NULLS NOT DISTINCT makes system names unique too (PostgreSQL 15+).
CREATE UNIQUE INDEX uq_categories_owner_name ON categories (user_id, lower(name)) NULLS NOT DISTINCT;

CREATE TABLE category_rules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NULL REFERENCES users(id) ON DELETE CASCADE,
    keyword     VARCHAR(255) NOT NULL,   -- normalized: upper case, no diacritics, single spaces
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_category_rules_owner_keyword ON category_rules (user_id, keyword) NULLS NOT DISTINCT;

-- A system category one user doesn't want to see.
CREATE TABLE user_hidden_categories (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, category_id)
);

-- 1. System categories: everything the old keyword table used, plus the fixed ones.
INSERT INTO categories (name, kind, color)
SELECT DISTINCT category, 'EXPENSE', NULL FROM category_mappings;

INSERT INTO categories (name, kind, color) VALUES
    ('Other',     'EXPENSE',  '#8A8F98'),
    ('Income',    'INCOME',   '#1E6B47'),
    ('Transfer',  'TRANSFER', '#5B6CFF'),
    ('Housing',   'EXPENSE',  NULL),
    ('Education', 'EXPENSE',  NULL)
ON CONFLICT DO NOTHING;

-- A default color per system category so charts and badges don't all look the same.
UPDATE categories SET color = CASE name
    WHEN 'Coffee Shop'     THEN '#8B5E3C'
    WHEN 'Fast Food'       THEN '#E4572E'
    WHEN 'Dining'          THEN '#F29E4C'
    WHEN 'Groceries'       THEN '#2E933C'
    WHEN 'Transportation'  THEN '#2F80ED'
    WHEN 'Travel'          THEN '#17A2B8'
    WHEN 'Online Shopping' THEN '#D63384'
    WHEN 'Shopping'        THEN '#B83280'
    WHEN 'Entertainment'   THEN '#9B51E0'
    WHEN 'Subscriptions'   THEN '#6F42C1'
    WHEN 'Utilities'       THEN '#E0A800'
    WHEN 'Health'          THEN '#DC3545'
    WHEN 'Housing'         THEN '#795548'
    WHEN 'Education'       THEN '#20C997'
    ELSE color END
WHERE user_id IS NULL AND color IS NULL;

-- 2. System rules from the old keyword table. Two-letter/three-letter generic words were noisy; drop them.
INSERT INTO category_rules (keyword, category_id)
SELECT DISTINCT ON (upper(m.keyword)) upper(m.keyword), c.id
FROM category_mappings m
JOIN categories c ON c.user_id IS NULL AND c.name = m.category
WHERE upper(m.keyword) NOT IN ('GAS', 'BP')
ORDER BY upper(m.keyword);

-- 3. Vietnamese merchants (keywords already normalized: no diacritics, upper case).
INSERT INTO category_rules (keyword, category_id)
SELECT v.keyword, c.id
FROM (VALUES
    ('HIGHLANDS', 'Coffee Shop'), ('HIGHLANDS COFFEE', 'Coffee Shop'), ('PHUC LONG', 'Coffee Shop'),
    ('KATINAT', 'Coffee Shop'), ('TRUNG NGUYEN', 'Coffee Shop'), ('THE COFFEE HOUSE', 'Coffee Shop'),
    ('CONG CAPHE', 'Coffee Shop'),
    ('CIRCLE K', 'Groceries'), ('GS25', 'Groceries'), ('FAMILYMART', 'Groceries'), ('7-ELEVEN', 'Groceries'),
    ('WINMART', 'Groceries'), ('BACH HOA XANH', 'Groceries'), ('CO.OPMART', 'Groceries'), ('COOPMART', 'Groceries'),
    ('LOTTE MART', 'Groceries'), ('AEON', 'Groceries'), ('MM MEGA MARKET', 'Groceries'), ('MINISTOP', 'Groceries'),
    ('SHOPEE', 'Online Shopping'), ('LAZADA', 'Online Shopping'), ('TIKI', 'Online Shopping'),
    ('TIKTOK SHOP', 'Online Shopping'),
    ('BE GROUP', 'Transportation'), ('XANH SM', 'Transportation'), ('PETROLIMEX', 'Transportation'),
    ('VIETJET', 'Travel'), ('VIETNAM AIRLINES', 'Travel'), ('BAMBOO AIRWAYS', 'Travel'),
    ('EVN', 'Utilities'), ('DIEN LUC', 'Utilities'), ('CAP NUOC', 'Utilities'), ('VIETTEL', 'Utilities'),
    ('VNPT', 'Utilities'), ('FPT TELECOM', 'Utilities'), ('MOBIFONE', 'Utilities'), ('VINAPHONE', 'Utilities'),
    ('BENH VIEN', 'Health'), ('PHONG KHAM', 'Health'), ('NHA THUOC', 'Health'), ('LONG CHAU', 'Health'),
    ('PHARMACITY', 'Health'), ('AN KHANG', 'Health'),
    ('CGV', 'Entertainment'), ('LOTTE CINEMA', 'Entertainment'), ('GALAXY CINEMA', 'Entertainment'),
    ('LUONG', 'Income'), ('SALARY', 'Income'),
    ('CHUYEN TIEN', 'Transfer'), ('TOPUP', 'Transfer'), ('NAP TIEN', 'Transfer')
) AS v(keyword, category)
JOIN categories c ON c.user_id IS NULL AND c.name = v.category
ON CONFLICT DO NOTHING;

-- 4. Link existing transactions. Names that match a system category use it; any other name becomes
--    a category of the transaction's owner, so nothing is lost. Rows without an owner fall back to Other.
ALTER TABLE transactions ADD COLUMN category_id UUID REFERENCES categories(id);

INSERT INTO categories (user_id, name, kind)
SELECT DISTINCT ON (t.created_by, lower(t.category)) t.created_by, t.category, 'EXPENSE'
FROM transactions t
WHERE t.created_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM categories s WHERE s.user_id IS NULL AND lower(s.name) = lower(t.category))
ORDER BY t.created_by, lower(t.category), t.category
ON CONFLICT DO NOTHING;

UPDATE transactions t SET category_id = c.id
FROM categories c
WHERE c.user_id IS NULL AND lower(c.name) = lower(t.category);

UPDATE transactions t SET category_id = c.id
FROM categories c
WHERE t.category_id IS NULL AND c.user_id = t.created_by AND lower(c.name) = lower(t.category);

UPDATE transactions SET category_id = (SELECT id FROM categories WHERE user_id IS NULL AND name = 'Other')
WHERE category_id IS NULL;

ALTER TABLE transactions ALTER COLUMN category_id SET NOT NULL;
-- The application no longer writes the old text column; V6 drops it.
ALTER TABLE transactions ALTER COLUMN category DROP NOT NULL;
CREATE INDEX idx_transactions_category_id ON transactions(category_id);
CREATE INDEX idx_transactions_owner_date ON transactions(created_by, date);
CREATE INDEX idx_categories_user ON categories(user_id);
CREATE INDEX idx_category_rules_user ON category_rules(user_id);

-- 5. Permissions (ADR-018 catalog): users manage their own categories; admins the system defaults.
INSERT INTO role_permissions (role_code, permission) VALUES
    ('USER',  'CATEGORY_WRITE'),
    ('ADMIN', 'CATEGORY_WRITE'),
    ('ADMIN', 'CATEGORY_ADMIN');
