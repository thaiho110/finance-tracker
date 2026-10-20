# ADR-024: AI Assistant (Provider Adapter, Tool Registry, Confirm-Cards)

**Status:** Accepted (implemented 2026-10-20: threads/messages + real Anthropic adapter behind the `ChatModel`
port, defaulting to `claude-opus-5-5`, falling back to a stub when no `ANTHROPIC_API_KEY` is set; tool-calling,
confirm-cards, streaming and refusal fallbacks remain follow-ups)
**Date:** 2026-10-19
**Deciders:** Thai Ho
**Amends:** [ADR-010](./010-domain-module-packages.md) (adds platform package `ai`, feature module `assistant`),
[ADR-018](./018-roles-permissions-features.md) / [ADR-020](./020-feature-modules-statements-tabs.md) (new module)
**Builds on:** [ADR-009](./009-multi-client-api.md) (rate limiting, multi-client), [ADR-021](./021-notifications-subsystem.md)

## Context

Finance tracking alone is thin. A conversational **AI assistant** lets a user *interact* with the whole app in
natural language — "how much did I spend on groceries in September?", "log a ₫200k charge to Minh", "call everyone
to dinner". The assistant must reach the same capabilities the modules already expose, and must never be able to do
what the user themselves could not.

Decided in grilling (R1-Q3/Q4, R2-Q3, R3-Q5): the assistant is an **action-taking agent** (not read-only, not
autonomous), whole-app, every mutating action **confirmed** by the user, bounded by the user's effective
permissions. The model runs behind a **provider-agnostic server-side adapter** so a local model can replace the
cloud one later without touching features.

## Decision

### 1. `ai` platform adapter, `assistant` feature module

- New **platform** package `ai`: one internal interface (`ChatModel`) over a chat-with-tools provider. The first
  implementation targets **Anthropic/Claude** (matches this environment) using the provider's native tool-use;
  model id and API key come from config/env. A local model (Ollama/vLLM) is a future implementation of the same
  interface — the privacy trade-off (finance data in prompts leaves the box) is accepted consciously for now.
- New **feature** module `assistant` (permission `ASSISTANT_USE`, granted to `USER` and `ADMIN`). The assistant UI
  and endpoints are gated by the module; a user with `ASSISTANT` off has no assistant surface.

### 2. Module-contributed tool registry

- Each feature publishes its own **tools** to a registry (platform): Spending exposes `create_transaction` /
  `query_spending`, Tabs `log_tab_entry`, Meals `call_household`, etc. A tool is a name, a JSON schema for its
  arguments, and a handler that calls the **same guarded service** the REST API calls.
- The tools offered to the model for a given request are **filtered by the caller's effective permissions**
  (ADR-018 + ADR-020 modules). A Tabs-only user's assistant is never even shown `create_transaction`, so the model
  cannot propose, let alone perform, something the user can't do by hand. This is the single security invariant.

### 3. Confirm-card on every mutation

- A **read** tool (query/summarize) executes directly and its result is folded into the answer.
- A **mutating** tool is never executed by the model turn. Instead the assistant returns a structured
  **proposed action** (the tool name + resolved arguments) rendered as a **confirm-card** in chat. The mutation
  happens only when the user taps confirm, which fires the normal guarded REST call. Declining discards it.
- Proposed actions live **inside** the assistant message as structured `tool_calls` (JSONB); there is no separate
  table, and an unconfirmed action is just an un-acted card.

### 4. Conversation history, streaming, cost control

- Conversations are **persisted per user** as threads of messages, so context carries across turns and sessions.
- Responses **stream** to clients over SSE.
- Usage is **rate-limited per user** reusing the existing Bucket4j setup (ADR-009), with a separate, tighter bucket
  for assistant calls since each one has real token cost.

## Schema

```sql
-- V16__assistant.sql
assistant_threads (
  id          UUID PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES users(id),
  title       VARCHAR(200),
  created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

assistant_messages (
  id          UUID PRIMARY KEY,
  thread_id   UUID NOT NULL REFERENCES assistant_threads(id) ON DELETE CASCADE,
  role        VARCHAR(20) NOT NULL,     -- USER | ASSISTANT | TOOL
  content     TEXT,
  tool_calls  JSONB NULL,               -- proposed actions / executed read-tool calls
  created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
-- index (thread_id, created_at)
```

## API

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/assistant/threads` | List threads. |
| `POST` | `/api/v1/assistant/threads` | Start a thread. |
| `GET` | `/api/v1/assistant/threads/{id}/messages` | Thread history. |
| `POST` | `/api/v1/assistant/threads/{id}/messages` | Send a message; SSE stream of the reply + any proposed actions. |
| `POST` | `/api/v1/assistant/actions/confirm` | `{ messageId, toolCallId }` → executes the confirmed mutation via its guarded service. |

All endpoints require `ASSISTANT_USE` and are user-scoped.

## Consequences

**Positive**
- One NL front door over every module, with no new authority: the permission filter on the tool registry means the
  assistant can only ever do what the user can.
- Provider-agnostic adapter keeps the local-first door open without blocking the feature now.
- Confirm-cards make mutations safe and auditable; the user is always the one who acts.

**Negative**
- Finance and household data appear in prompts sent to a cloud provider — a real privacy cost, accepted explicitly.
- Token cost per request; needs its own rate bucket and some budget awareness.
- Every feature must define and maintain its tool set, and keep the tool handler in lockstep with the REST service.
- Streaming + tool-use + confirm handshake is non-trivial client work across web, desktop and mobile.

## Alternatives considered

- **Read-only assistant.** Rejected: "interacting" with the app implies doing things, not just asking.
- **Fully autonomous agent (no confirm).** Rejected: unacceptable for money and for messages to other people.
- **Self-hosted model first.** Rejected now: hosting a capable chat+tool-use model is a large ops/hardware bet that
  would stall the feature; the adapter lets it come later.
- **Client-side LLM calls.** Rejected: would leak the API key and bypass the server-side permission filter that is
  the whole security model.

## Open questions

- How much data to put in context vs. retrieve on demand via read-tools (cost vs. capability).
- Whether a confirmed action's result is itself summarized back into the thread automatically.
