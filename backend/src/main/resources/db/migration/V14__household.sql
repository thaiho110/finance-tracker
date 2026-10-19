-- ADR-022: Household — addressing, not shared ownership. A household is a set of users who live together,
-- joined by invite; a user belongs to at most one (enforced by UNIQUE on household_members.user_id). It grants
-- the ability to address the group (e.g. a meal call, ADR-025); it grants no access to anyone else's data.
--
-- Deviation from the ADR: household_members uses a surrogate id + UNIQUE(user_id) rather than a composite
-- PRIMARY KEY (household_id, user_id). UNIQUE(user_id) already enforces the one-household-per-user rule and
-- maps more simply in JPA.
--
-- Deferred: contacts.linked_user_id (ADR-022 §4) waits for ADR-020's `contacts` table, which is not on main.

CREATE TABLE households (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    created_by UUID         NOT NULL REFERENCES users(id),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE household_members (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID      NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    user_id      UUID      NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    joined_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_household_members_household ON household_members (household_id);

CREATE TABLE household_invites (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id  UUID        NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    invitee_email VARCHAR(255),
    code          VARCHAR(64) NOT NULL UNIQUE,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING | ACCEPTED | EXPIRED | REVOKED
    invited_by    UUID        NOT NULL REFERENCES users(id),
    expires_at    TIMESTAMP   NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_household_invites_household ON household_invites (household_id);

-- No role_permissions rows: household is platform and always on (ADR-022 §1), gated only by being signed in.
