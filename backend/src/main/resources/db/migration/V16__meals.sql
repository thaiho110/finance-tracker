-- ADR-025: the household meal call. One member calls everyone to the table; the others are notified (in-app,
-- ADR-021) and answer COMING/LATER/SKIP. Entirely separate from any finance surface.

CREATE TABLE meal_calls (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id   UUID         NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    caller_user_id UUID         NOT NULL REFERENCES users(id),
    message        VARCHAR(200),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_meal_calls_household ON meal_calls (household_id, created_at DESC);

CREATE TABLE meal_call_acks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_call_id  UUID        NOT NULL REFERENCES meal_calls(id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users(id),
    response      VARCHAR(10) NOT NULL,     -- COMING | LATER | SKIP
    responded_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (meal_call_id, user_id)
);

-- Permissions MEALS_CALL / MEALS_RESPOND were added in V15 (ADR-020 §4) and granted to USER and ADMIN; the
-- MEALS module gates them, so a user who turns Meals off loses them via effective permissions.
