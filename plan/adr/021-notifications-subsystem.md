# ADR-021: Notifications Subsystem (In-App Feed + Push)

**Status:** Proposed
**Date:** 2026-10-19
**Deciders:** Thai Ho
**Amends:** [ADR-010](./010-domain-module-packages.md) (adds a new platform package `notifications`)
**Builds on:** [ADR-001](./001-modular-monolith.md) (one process), [ADR-009](./009-multi-client-api.md) (multi-client, `X-Client-Id`)
**Depended on by:** [ADR-023](./023-integrations-webhooks.md), [ADR-024](./024-ai-assistant.md), [ADR-025](./025-meals.md)

## Context

Kept is being re-scoped from a finance tracker into a multi-purpose app of modules (see [ADR-022](./022-household.md)).
Three of the planned capabilities — meal calls, the AI assistant, and inbound webhooks — all need to **push a message
to a user** across web, desktop and mobile. Today Kept has no push channel and no notification history: the only
"warning" surface is the ADR-019 budget banner, computed per request and never stored.

Building a notification path per feature would guarantee drift (three payload shapes, three delivery code paths).
A notification is also the **connective tissue** that makes separate modules feel like one app, so it belongs in the
platform, not in any one feature.

## Decision

### 1. One platform package `notifications`, always on

- `notifications` is a **platform** package (ADR-010 / ADR-020 §1): domain-agnostic, importable by any feature,
  importing no feature. It is **not a module** (ADR-020 §4) — every account can receive notifications; there is
  nothing to turn off here. Per-type muting and quiet hours are deferred to a later ADR.
- Features never deliver anything themselves. They **publish** a `Notification` and the subsystem decides channels
  and transports.

### 2. One `Notification` concept

A notification has: `recipientUserId`, `type` (a string owned by the producing feature, e.g. `meal.call`,
`assistant.summary`, `webhook.triggered`, `budget.over`), `title`, `body`, `data` (JSONB, free-form payload the
client uses to deep-link / render actions), the `channels` it was routed to, `readAt`, `createdAt`.

- **In-app feed:** every notification is persisted and listed per user, newest first, with read/unread.
- **Push:** notifications whose type routes to push are also delivered to the user's registered devices.
- **Per-type channel routing:** each `type` declares its channels in code (a small registry), so e.g. a meal call
  is `PUSH + IN_APP` while an assistant summary is `IN_APP` only. Routing lives with the producing feature's type
  definition, not scattered at call sites.

### 3. Transports, in order of delivery

| Transport | Clients | Status in this ADR |
|---|---|---|
| In-app feed (`GET /api/v1/notifications`) | web, desktop, mobile | Ships |
| **Web Push (VAPID)** | web, Electron desktop | Ships |
| FCM | react-native Android | Tracked follow-up **within this ADR**, not a new ADR |
| APNs | react-native iOS | Tracked follow-up **within this ADR** |

Web Push covers web and desktop immediately; FCM/APNs are additive transports behind the same `push_tokens` table
and the same publish call, so no feature changes when they land.

### 4. Device registration

Clients register a push token after login and refresh it on change. One user has many devices; a token is unique.
Logout removes the token. Delivery failures (expired/invalid token) prune the row.

## API

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/notifications?unread=&page=` | The in-app feed, newest first. |
| `POST` | `/api/v1/notifications/{id}/read` | Mark one read. |
| `POST` | `/api/v1/notifications/read-all` | Mark all read. |
| `GET` | `/api/v1/notifications/unread-count` | Badge count. |
| `POST` | `/api/v1/push/tokens` | `{ client, token }` — register / refresh this device. |
| `DELETE` | `/api/v1/push/tokens` | `{ token }` — deregister (on logout). |
| `GET` | `/api/v1/push/vapid-public-key` | Web Push subscription key. |

All endpoints are user-scoped; no new module permission (notifications are always on). The feed is read-only to
clients — only features (server-side) create notifications.

## Schema

```sql
-- V13__notifications.sql
notifications (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id),
  type          VARCHAR(50) NOT NULL,     -- 'meal.call', 'assistant.summary', 'webhook.triggered', ...
  title         VARCHAR(200) NOT NULL,
  body          VARCHAR(1000),
  data          JSONB,                    -- deep-link target, quick actions, producer payload
  channels      VARCHAR(50)[] NOT NULL,   -- {'IN_APP'}, {'IN_APP','PUSH'}
  read_at       TIMESTAMP NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
-- index (user_id, created_at DESC), partial index (user_id) WHERE read_at IS NULL

push_tokens (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id),
  client        VARCHAR(40) NOT NULL,     -- ADR-009 X-Client-Id family: web / desktop / mobile
  transport     VARCHAR(20) NOT NULL,     -- 'WEB_PUSH' | 'FCM' | 'APNS'
  token         TEXT NOT NULL UNIQUE,
  created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
  last_seen_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
-- index (user_id)
```

## Consequences

**Positive**
- One payload shape and one delivery path for every producer; features just publish.
- Notifications become durable (feed + read state), not transient like the budget banner.
- New transports (FCM/APNs) and new producers add no cross-cutting code.

**Negative**
- New infra: VAPID keys to manage, a service worker on web, Electron notification wiring.
- `data` as JSONB is schema-less; each client must handle an unknown `type` gracefully (ignore + show title/body).
- Mobile push (FCM/APNs) requires platform credentials and store setup, so mobile lags web/desktop by a transport.

## Alternatives considered

- **Per-feature notifications.** Rejected: three payload shapes and three delivery paths, drift guaranteed.
- **Email/SMS as a channel.** Deferred: no SMTP/provider in the stack; push + in-app cover the use cases now.
- **A message broker for fan-out.** Rejected for the same reason as ADR-023's event bus: it is one process
  (ADR-001); in-process dispatch is enough. Revisit if Kept is ever split.

## Open questions

- Quiet hours and per-type user muting — deferred to a later ADR once there are enough noisy types to justify it.
- Whether desktop (Electron) uses Web Push or a native notification bridge; both reach the same `push_tokens` row.
