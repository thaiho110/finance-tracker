# Kept

Personal finance tracking tool with CSV import, receipt OCR, and multi-client support.

## Language

**Transaction**:
A single financial record with a date, merchant, amount, and category. The core data entity of the system.
_Avoid_: Record, entry, line item

**ParsedTransaction**:
A transaction drafted by the Transaction Intake (from a CSV row or a receipt), shown in a preview grid for user review before being saved. Not yet persisted.
_Avoid_: Raw transaction, CSV row

**RawEntry**:
One transaction as an import source read it (a CSV row, a scanned receipt) before categorizing or duplicate checks. Fields the source could not read are null.
_Avoid_: Raw row, raw transaction

**Transaction Intake**:
The single path every import source goes through (`TransactionIntake.draft`): turns RawEntries into ParsedTransactions with a category and a duplicate flag. CSV import and receipt OCR are adapters in front of it.
_Avoid_: Processing engine, pipeline

**Possible duplicate**:
A ParsedTransaction whose amount (ignoring sign) matches a saved transaction, or an earlier entry in the same upload, dated within 2 days either side. Flagged for review, never removed automatically.
_Avoid_: Duplicate (as if certain)

**Receipt**:
An uploaded receipt image that has been processed by OCR to extract merchant, date, total, and line items.
_Avoid_: Receipt image, photo, scan

**Category**:
A classification label assigned to a transaction (e.g., "Groceries", "Transportation"). Derived from keyword matching against the merchant name.
_Avoid_: Tag, label, group

**SourceType**:
The origin of a transaction. One of: CSV (imported from bank statement), OCR (extracted from receipt), MANUAL (entered by hand).
_Avoid_: Source, type, import method

**ApiClient**:
An HTTP client wrapper that handles authentication headers, token refresh, and `X-Client-Id` injection. Each platform provides its own implementation (web uses RTK Query with custom `baseQuery`; desktop and mobile use platform-specific HTTP clients).
_Avoid_: HTTP client, API wrapper, fetcher

**TokenStorage**:
An interface for persisting JWT tokens. Each platform provides its own: web uses `localStorage`, desktop uses Electron `safeStorage`, mobile uses `react-native-keychain`.
_Avoid_: Token store, keychain, storage

**Shared Types**:
Pure TypeScript interfaces auto-generated from the backend's OpenAPI spec using `openapi-typescript`. The single source of truth for API contracts shared across all clients.
_Avoid_: DTOs, models, generated types

**Aggregation Endpoint**:
A backend endpoint (`GET /api/v1/transactions/summary`) that returns pre-computed totals by category, monthly trends, and summary statistics. Avoids client-side computation over large datasets.
_Avoid_: Stats endpoint, dashboard API, summary API

## Platform & multi-capability (ADR-020 ff.)

**Module**:
A user-facing part of Kept a user can turn on or off **for themselves** (ADR-020 §4), grouping permissions — e.g. `SPENDING`, `TABS`, `ASSISTANT`, `INTEGRATIONS`, `MEALS`. Turning a module off hides and blocks, never deletes. Distinct from the two senses below.
_Avoid_: Feature (bare), plugin, section

**Feature permission**:
A `FEATURE`-category permission from ADR-018 that an admin can switch on/off per user (e.g. `STATEMENTS`, `TABS`, `ASSISTANT_USE`). What a user has; a module is what a user chooses from what they have.
_Avoid_: Feature (bare)

**Feature package**:
A code package that is a user-facing domain (`transaction`, `tab`, `meals`, …), as opposed to a **platform package** (domain-agnostic, reusable: `common`, `auth`, `notifications`, `household`, `events`, `ai`, `ledger`, `document`, `people`). ArchUnit enforces that platform never imports feature (ADR-020 §1).
_Avoid_: Feature (bare), module (that's the user's choice, not the code)

**Household**:
A set of Kept users who live together, joined by invite; a user belongs to **at most one** (ADR-022). It grants **addressing** ("notify my household"), never shared ownership of data — all data stays `user_id`-scoped. Not multi-tenancy.
_Avoid_: Tenant, group, family account, shared account

**Meal call**:
One household member's "come to the table" signal (ADR-025): a persisted event that pushes a notification to every other member, who reply `COMING | LATER | SKIP`. No money, no vendor, no food ordering.
_Avoid_: Meal, reminder, order, invite

**Notification**:
A platform message to one user (ADR-021): `type`, `title`, `body`, `data`, `channels`, read state. Persisted as an in-app feed and optionally delivered via push. Always on; produced by features, never by clients.
_Avoid_: Alert, message, toast, push (push is one channel of a notification)

**Push token**:
A per-device credential (Web Push / FCM / APNs) registered after login so notifications can reach that device. One user has many; unique per device (ADR-021).
_Avoid_: Device id, subscription (that's an outbound webhook)

**Event** (internal):
A `DomainEvent` published on the in-process bus (ADR-023), carrying the `userId` it concerns. Notifications and outbound webhooks are listeners. Not a message-broker event — Kept is one process.
_Avoid_: Message, signal, webhook

**Webhook endpoint** (inbound):
A user-owned URL `POST /api/v1/webhooks/{token}` that an external system calls with an HMAC-signed body to raise an internal **Event** (ADR-023). Authenticated by token + HMAC + replay window, not JWT.
_Avoid_: Hook, callback, inbound URL

**Subscription** (outbound):
A user-registered target URL that Kept POSTs a signed payload to when a matching **Event** fires (ADR-023). The outbound counterpart of a webhook endpoint.
_Avoid_: Webhook (ambiguous direction), listener, drain

**Assistant**:
The AI chatbot module (ADR-024): an action-taking agent over the whole app, calling module-contributed tools bounded by the user's permissions, with every mutation gated behind a user-confirmed card.
_Avoid_: AI, bot, chatbot (in code/UI prefer "Assistant")

**Confirm-card**:
A proposed mutating action the Assistant returns in chat (tool name + resolved arguments); the mutation runs only when the user taps confirm, via the same guarded service the REST API uses (ADR-024).
_Avoid_: Action, suggestion, prompt
