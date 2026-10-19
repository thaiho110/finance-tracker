# ADR-025: Meals — the Household Meal Call

**Status:** Proposed
**Date:** 2026-10-19
**Deciders:** Thai Ho
**Amends:** [ADR-010](./010-domain-module-packages.md) (adds feature module `meals`),
[ADR-020](./020-feature-modules-statements-tabs.md) (new user-chosen module)
**Builds on:** [ADR-021](./021-notifications-subsystem.md) (push), [ADR-022](./022-household.md) (the group to call)

## Context

A finance tracker alone isn't enough to make Kept a daily-use app. The first non-finance capability is small and
concrete: a **meal call** — one household member taps "come to the table" and everyone else in the household is
notified, and can reply whether they're coming. It is a social/household signal: no money, no vendor, no delivery
(grilling R1-Q2 → option (a)).

Meals is the capability that pulls the new foundations together: it needs the **household** to know who to call
([ADR-022](./022-household.md)) and the **notification subsystem** to reach them ([ADR-021](./021-notifications-subsystem.md)).
It is intentionally built **last** of the new capabilities, on top of stable seams.

## Decision

### 1. `meals` feature module

- New **feature** package `meals` and a toggleable module `MEALS` (ADR-020 §4), with permissions `MEALS_CALL`
  (start a call) and `MEALS_RESPOND` (reply to one), granted to `USER` and `ADMIN`.
- `MEALS` is useful only with a household (ADR-022). A user in no household sees the module prompt them to create or
  join one; the call button is disabled until then.

### 2. The meal call

- A member with `MEALS_CALL` starts a call with an optional short message (default e.g. "Come to the table").
- Every **other** household member is sent a `meal.call` notification (ADR-021), routed to **PUSH + IN_APP**, with
  quick-reply actions in the push payload.
- A call is **persisted** (not fire-and-forget) so the caller can see responses and so there is a short history.

### 3. Responses

- A recipient with `MEALS_RESPOND` answers `COMING | LATER | SKIP` (one row per member; a later answer replaces the
  earlier one).
- The caller — and other members — see who's coming in real time. Responses optionally notify the caller
  (`IN_APP`), not the whole household, to avoid noise.
- A call has no explicit "end": it is a moment, listed under recent calls; a new call supersedes it in the UI.

### 4. Reuses existing seams, adds no finance

- Meals writes **nothing** to transactions, tabs, budgets, overview or statements — it is entirely separate, exactly
  as Tabs is kept out of spending in ADR-020.
- An external trigger can start a meal call via an inbound webhook (ADR-023, anchor use case): `integration.trigger`
  → a meal call, for e.g. a kitchen button. This is additive and needs no new meals code beyond a listener.

## Schema

```sql
-- V17__meals.sql
meal_calls (
  id              UUID PRIMARY KEY,
  household_id    UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  caller_user_id  UUID NOT NULL REFERENCES users(id),
  message         VARCHAR(200),
  created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
-- index (household_id, created_at DESC)

meal_call_acks (
  meal_call_id  UUID NOT NULL REFERENCES meal_calls(id) ON DELETE CASCADE,
  user_id       UUID NOT NULL REFERENCES users(id),
  response      VARCHAR(10) NOT NULL,     -- COMING | LATER | SKIP
  responded_at  TIMESTAMP NOT NULL DEFAULT NOW(),
  PRIMARY KEY (meal_call_id, user_id)
);
```

## API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/meals/calls` | `{ message? }` — start a call; notifies the household. Requires `MEALS_CALL` + membership. |
| `GET` | `/api/v1/meals/calls?recent=` | Recent calls for the caller's household, with responses. |
| `GET` | `/api/v1/meals/calls/{id}` | One call + who's coming. |
| `POST` | `/api/v1/meals/calls/{id}/respond` | `{ response }` — COMING / LATER / SKIP. Requires `MEALS_RESPOND`. |

All endpoints are household-membership-checked; a user may only see/act on calls in their own household.

## Consequences

**Positive**
- A genuinely daily, zero-finance reason to open the app — the thing that makes Kept more than a tracker.
- Proves the whole stack end-to-end: household addressing → notification → push → quick-reply → persisted response.
- No distortion of any finance surface, by construction.

**Negative**
- Depends on push actually working on the user's platforms (web/desktop first; mobile once FCM/APNs land, ADR-021).
- Quick-reply actions in push payloads differ per transport and are the fiddliest client work.
- Value collapses for a solo user with no household — accepted; Meals is opt-in per ADR-020 §4.

## Alternatives considered

- **Meal reminders / scheduler, food ordering, or a request-to-a-person.** The other R1-Q2 readings. Rejected for
  the first cut: the household broadcast needs no vendor, no payments and no scheduler, and matches the household
  direction.
- **Fire-and-forget call (no persistence).** Rejected: "who's coming?" is most of the value, and it needs a row.
- **Reusing Tabs contacts as recipients.** Rejected: contacts need no account and can't receive push (ADR-022).

## Open questions

- Whether a call should auto-expire from the UI after some time, or only be superseded by the next call.
- Whether LATER should schedule a follow-up nudge (needs the deferred scheduling/quiet-hours work from ADR-021).
