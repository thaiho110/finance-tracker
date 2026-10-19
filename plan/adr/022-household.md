# ADR-022: Household (Addressing, Not Shared Ownership)

**Status:** Accepted (backend implemented 2026-10-19; `contacts.linked_user_id` deferred until ADR-020's
`contacts` table exists; `household_members` uses a surrogate id + `UNIQUE(user_id)` rather than a composite PK)
**Date:** 2026-10-19
**Deciders:** Thai Ho
**Amends:** [ADR-020](./020-feature-modules-statements-tabs.md) (reverses its "multi-tenancy/household rejected"
stance, and makes finance a peer module rather than the privileged core)
**Builds on:** [ADR-018](./018-roles-permissions-features.md) (users, roles), [ADR-021](./021-notifications-subsystem.md) (addressing a group)
**Depended on by:** [ADR-025](./025-meals.md)

## Context

The re-scope (grilling session, 2026-10-19) adds a **meal call**: one household member signals "come to the table"
and the others are notified ([ADR-025](./025-meals.md)). That is impossible without a notion of **a group of users
to address**. ADR-020 explicitly rejected a household/multi-tenancy ("Kept's data is already scoped by `user_id`…
a shared household can be added to Tabs later"). That "later" is now.

The key distinction that keeps this cheap: a household is about **addressing**, not **ownership**. We are not making
finances shared. We only need "notify the people I live with" and "link a Tabs contact to a real person."

ADR-020 also assumed finance (Spending) is the privileged core (home screen, nav, registration default). This ADR
demotes finance to a **peer module**: a user may run Kept for only Meals + Assistant with no finance at all.

## Decision

### 1. Household is a platform concept, addressing only

- New **platform** package `household` (ADR-010), always on — not a toggleable module. Belonging to a household
  grants the ability to *address* that group; it grants **no access to anyone else's data**. All finance, tabs,
  statements and notifications stay strictly `user_id`-scoped. This is **not multi-tenancy** and is **not** a shared
  ledger.
- A user belongs to **at most one household**. This keeps "call the household" unambiguous and the schema small.
  Multiple households, if ever needed, are a later additive change.

### 2. Membership and invites

- A user creates a household (becomes its first member) or joins one by **invite**. An invite is a code/email with
  an expiry and a status (`PENDING | ACCEPTED | EXPIRED | REVOKED`).
- Joining a household the user is already in, or a second household, is rejected (one-household rule).
- Leaving a household is allowed; the last member leaving deletes the (now empty) household.
- Membership is flat: there is no "household admin" role in this ADR. Any member may invite; any member may leave.
  (A manage-permission can be added later if abuse appears.)

### 3. Finance becomes a peer module (amends ADR-020)

- ADR-020's effective-permission model and module machinery stand. What changes is the **assumption**: no client
  may assume `SPENDING` is on. Navigation, the home screen and the registration default are built purely from
  `/auth/me`'s `modules` (ADR-020 §4). A Meals-only user never sees a finance surface.
- Registration's "What do you want to use Kept for?" (ADR-020 §4) is the literal entry point to this: picking
  neither Spending nor Tabs is valid as long as **at least one** module stays on (ADR-020 §4 rule unchanged).

### 4. Tabs contacts may link to a household member

- The `contacts.linked_user_id` column that ADR-020's grilling (R1-Q7) **dropped** comes back **here**, where it has
  a real home: a Tabs contact may optionally point at a **member of the same household**. This is still only a link
  (for display and future convenience); it does **not** let the linked person see their balance — that remains the
  open question ADR-020 raised, still deferred.

## Schema

```sql
-- V14__household.sql
households (
  id          UUID PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  created_by  UUID NOT NULL REFERENCES users(id),
  created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

household_members (
  household_id UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  user_id      UUID NOT NULL REFERENCES users(id),
  joined_at    TIMESTAMP NOT NULL DEFAULT NOW(),
  PRIMARY KEY (household_id, user_id),
  UNIQUE (user_id)                    -- enforces: at most one household per user
);

household_invites (
  id            UUID PRIMARY KEY,
  household_id  UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  invitee_email VARCHAR(255),
  code          VARCHAR(40) NOT NULL UNIQUE,
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING|ACCEPTED|EXPIRED|REVOKED
  invited_by    UUID NOT NULL REFERENCES users(id),
  expires_at    TIMESTAMP NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- contacts.linked_user_id re-added here (ADR-020 §6 contacts table), referencing a household member.
ALTER TABLE contacts ADD COLUMN linked_user_id UUID NULL REFERENCES users(id);
```

## API

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/household` | The caller's household + members, or `null` if none. |
| `POST` | `/api/v1/household` | Create a household `{ name }` (fails if already in one). |
| `POST` | `/api/v1/household/invites` | Invite `{ email }` → returns a code. |
| `POST` | `/api/v1/household/join` | Accept `{ code }`. |
| `DELETE` | `/api/v1/household/members/me` | Leave; deletes the household if now empty. |

Household endpoints need no module permission (platform, always on); they are user-scoped and membership-checked.

## Consequences

**Positive**
- Meals, and any future "notify the people I live with" feature, has a real recipient set.
- The cost stays tiny: addressing only, no shared ownership, no row-level tenant rewrite.
- Finance-as-peer is made explicit, closing the ADR-020 gap where clients assumed Spending.

**Negative**
- A genuinely new domain concept (household, membership, invites) with its own lifecycle edge cases.
- The one-household rule is a `UNIQUE (user_id)` today; relaxing it later is a real (if additive) migration.
- `contacts.linked_user_id` returns as a link with no reader beyond display — deliberately minimal, see below.

## Alternatives considered

- **No household; meal call notifies only your own devices.** Rejected: a "call to the table" to yourself is
  pointless; the feature needs other people.
- **Full multi-tenancy (shared finances).** Rejected as in ADR-020: tenants isolate organizations and would force a
  row-level rewrite; Kept needs addressing, not shared ownership.
- **Reuse Tabs contacts as the "household".** Rejected: contacts need no account and can't receive a push; a meal
  call must reach real Kept users with devices.

## Open questions

- Whether a linked contact (`linked_user_id`) ever sees their own balance — still deferred (raised in ADR-020).
- Whether households need an admin/owner role — deferred until there is a reason (abuse, kick-member).
