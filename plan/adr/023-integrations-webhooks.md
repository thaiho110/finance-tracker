# ADR-023: Integrations — Internal Event Bus and External Webhooks

**Status:** Proposed
**Date:** 2026-10-19
**Deciders:** Thai Ho
**Amends:** [ADR-010](./010-domain-module-packages.md) (adds platform package `events`, feature module `integrations`)
**Builds on:** [ADR-001](./001-modular-monolith.md) (one process), [ADR-021](./021-notifications-subsystem.md) (events raise notifications)

## Context

The app should connect to the outside world: external devices and services (home automation, IFTTT, a bank push)
should be able to make things happen in Kept, and Kept should be able to tell other systems when something happens.
This also needs an **internal event mechanism**, because meal calls, notifications and outbound webhooks are all
producers/consumers of the same "something happened" signal.

Anchor use case (chosen in grilling, R2-Q6): a **home-automation trigger** — an external device/service POSTs a
signed event to Kept, which raises a notification or a meal call. It exercises the whole path end-to-end
(inbound signed webhook → internal event → notification → push) and ties directly to the Meals/Household work, so
nothing is designed in a vacuum. A **bank/transaction push** that creates a draft transaction (reusing Transaction
Intake) is the intended **second** integration and shapes the event schema but is not built here.

## Decision

### 1. Internal event bus: in-process Spring events

- New **platform** package `events` with a small typed `DomainEvent` hierarchy and a thin publisher over Spring's
  `ApplicationEventPublisher`. In a modular monolith (ADR-001) this is one process; **no message broker**
  (Kafka/Rabbit) now — it is pure ops cost with no payoff until Kept is split into services.
- Notification dispatch (ADR-021) and outbound-webhook dispatch are **listeners** on this bus. Features publish a
  `DomainEvent`; they do not call notifications or webhooks directly.
- Events are user-scoped: every `DomainEvent` carries the `userId` it concerns, so listeners fan out to the right
  recipient / the right user's subscriptions.

### 2. Outbound webhooks: subscribe a URL to event types

- A user registers a subscription: a target URL, a shared secret, and the event types it wants.
- When a matching `DomainEvent` fires, the subscription listener POSTs a JSON payload signed with **HMAC-SHA256**
  (`X-Kept-Signature`) over the raw body plus a timestamp header. Deliveries are logged with status and retried with
  backoff a bounded number of times; permanent failures mark the delivery failed and are visible to the user.

### 3. Inbound webhooks: signed endpoints that raise events

- A user creates an inbound endpoint and gets a unique opaque **token** (in the URL) and a **secret**.
- An external caller `POST`s to `/api/v1/webhooks/{token}` with an HMAC-SHA256 signature over the body and a
  timestamp within a short **replay window**; a stale or mis-signed request is rejected (401) and logged.
- A valid inbound call raises a `DomainEvent` of a configured type for the endpoint's owner. For the anchor use case
  the event type is `integration.trigger`, which the notification/meals listeners turn into a notification or
  (if configured) a meal call. Mapping an inbound trigger to richer actions (e.g. a draft transaction) is additive:
  a new event type + a listener in the owning feature, with no change to the webhook plumbing.

### 4. `integrations` is a toggleable module (amends ADR-020 §4)

- New `FEATURE` permission `INTEGRATIONS_MANAGE`, in a new module `INTEGRATIONS`, granted to `USER` and `ADMIN`.
  Managing endpoints/subscriptions requires it; a user with the module off has no integration surface.
- The internal `events` bus is **platform** and always present — the module only gates the external, user-managed
  webhook surface.

## Schema

```sql
-- V15__integrations.sql
webhook_endpoints (            -- inbound
  id             UUID PRIMARY KEY,
  owner_user_id  UUID NOT NULL REFERENCES users(id),
  name           VARCHAR(100) NOT NULL,
  token          VARCHAR(64) NOT NULL UNIQUE,   -- opaque, in the URL
  secret         VARCHAR(128) NOT NULL,         -- HMAC key
  event_type     VARCHAR(50) NOT NULL DEFAULT 'integration.trigger',
  active         BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

webhook_subscriptions (        -- outbound
  id             UUID PRIMARY KEY,
  owner_user_id  UUID NOT NULL REFERENCES users(id),
  target_url     TEXT NOT NULL,
  secret         VARCHAR(128) NOT NULL,
  event_types    VARCHAR(50)[] NOT NULL,
  active         BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

webhook_deliveries (           -- outbound audit / retry log
  id              UUID PRIMARY KEY,
  subscription_id UUID NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
  event_type      VARCHAR(50) NOT NULL,
  status          VARCHAR(20) NOT NULL,          -- PENDING|DELIVERED|FAILED
  attempts        INT NOT NULL DEFAULT 0,
  response_code   INT NULL,
  last_attempt_at TIMESTAMP NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
-- index (owner_user_id) on endpoints/subscriptions; (subscription_id, created_at) on deliveries
```

## API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/webhooks/{token}` | **Inbound.** Signed external call → raises a `DomainEvent`. No JWT; HMAC-authenticated. |
| `GET`/`POST`/`PATCH`/`DELETE` | `/api/v1/integrations/endpoints` | Manage inbound endpoints (`INTEGRATIONS_MANAGE`). |
| `GET`/`POST`/`PATCH`/`DELETE` | `/api/v1/integrations/subscriptions` | Manage outbound subscriptions. |
| `GET` | `/api/v1/integrations/deliveries?subscriptionId=` | Delivery log for debugging. |

## Consequences

**Positive**
- One internal bus serves notifications, meals and outbound webhooks; producers stay decoupled from delivery.
- The anchor use case ships a complete inbound→event→notification→push path that later integrations reuse.
- Signing (HMAC + timestamp + replay window) is defined once for both directions.

**Negative**
- Inbound endpoints are a new unauthenticated-by-JWT surface; security rests entirely on the token + HMAC + replay
  window, so those must be implemented carefully (constant-time compare, short window, per-endpoint secret).
- Outbound delivery needs retry/backoff and a dead-letter notion, i.e. real background work in the monolith.
- In-process events mean a listener's failure must not break the publisher's transaction — listeners run after
  commit where they have side effects (push, HTTP), and their own failures are isolated and logged.

## Alternatives considered

- **Message broker (Kafka/Rabbit) for the bus.** Rejected now: one process (ADR-001); brokers are ops cost with no
  payoff until services are split. The `DomainEvent` abstraction leaves the door open.
- **Polling/no inbound webhooks.** Rejected: the whole point is letting external devices push into Kept in real time.
- **A third-party automation platform (Zapier) instead of our own webhooks.** Rejected for a self-hosted, local-first
  app; a signed HTTP contract keeps it in-house and free.

## Open questions

- Exact retry policy and whether failed outbound deliveries raise an in-app notification to the owner.
- Whether inbound endpoints should support an allow-list of source IPs in addition to HMAC.
