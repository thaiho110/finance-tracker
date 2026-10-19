# ADR-021: Notifications Subsystem (In-App Feed)

**Status:** Accepted (in-app only; push deferred — 2026-10-19)
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

### 3. Delivery: in-app only

- **In-app feed** (`GET /api/v1/notifications`): every notification is persisted and shown in the app with a
  read/unread state and an unread badge. Clients see new notifications by refetching the feed / polling the
  unread count while open. This is the whole of delivery for now.
- **Push is deferred** (decision 2026-10-19). Browser/OS push (Web Push / FCM / APNs) and device-token
  registration are intentionally **not** built: they add real crypto, a schema for subscriptions, client
  service workers and platform credentials, for little gain while the app is the surface the user is already
  in. The subsystem is designed so a push transport can be added later as an adapter (a `PushSender` port)
  without changing producers — but no device tokens exist today.
- Live, real-time in-app delivery (SSE/WebSocket pop-ups rather than polling) is a possible later enhancement,
  not built here.

## API

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/notifications?unread=&page=` | The in-app feed, newest first. |
| `POST` | `/api/v1/notifications/{id}/read` | Mark one read. |
| `POST` | `/api/v1/notifications/read-all` | Mark all read. |
| `GET` | `/api/v1/notifications/unread-count` | Badge count. |

All endpoints are user-scoped; no new module permission (notifications are always on). The feed is read-only to
clients — only features (server-side) create notifications. The `/api/v1/push/*` endpoints are deferred with push.

## Schema

As built (in-app only). ADR's richer `data JSONB` / `channels[]` are reduced to `deep_link`; the `push_tokens`
table is deferred with push.

```sql
-- V13__notifications.sql
notifications (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type          VARCHAR(50) NOT NULL,     -- 'meal.call', 'assistant.summary', 'budget.over', ...
  title         VARCHAR(200) NOT NULL,
  body          VARCHAR(1000),
  deep_link     VARCHAR(500),             -- client route to open when tapped
  read_at       TIMESTAMP NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
-- index (user_id, created_at DESC), partial index (user_id) WHERE read_at IS NULL
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
