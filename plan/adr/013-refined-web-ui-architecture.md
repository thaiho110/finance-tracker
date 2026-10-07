# ADR-013: Refined Web UI Architecture

After grilling ADR-012, six decisions were refined. This ADR captures the changes that are hard to reverse, surprising without context, or the result of real trade-offs.

**Status:** Accepted  
**Date:** 2026-10-07  
**Deciders:** Thai Ho

## Decision 1: Shared Package Is Types-Only, Auto-Generated

### What

`@finance-tracker/shared` contains **only** auto-generated TypeScript interfaces from the backend's OpenAPI spec. No runtime code (`ApiClient`, `TokenStorage`, validation) lives in shared.

### Why

- The backend already has SpringDoc OpenAPI with `@Schema` annotations on every DTO — the OpenAPI spec at `/v3/api-docs` is a complete, machine-readable contract
- `openapi-typescript` generates clean, tree-shakeable TypeScript interfaces from that spec
- Changing a Java DTO field breaks the frontend build at compile time — no drift possible
- Each platform (web, desktop, mobile) has fundamentally different HTTP client needs (fetch vs Electron IPC vs native HTTP); a shared `ApiClient` would accumulate platform-specific branches or be too abstract to be useful

### Consequences

- **Positive**: Zero manual type maintenance. Backend changes propagate automatically.
- **Positive**: Shared package has zero runtime dependencies — pure interfaces, no bundle cost.
- **Positive**: Each platform owns its HTTP layer and can use the best tool for its environment.
- **Negative**: Requires the backend to be running (or its OpenAPI spec cached) to regenerate types during development.

### Tooling

```bash
# In pnpm workspace — add to root package.json scripts
pnpm gen:types  # runs: openapi-typescript http://localhost:8080/v3/api-docs -o packages/shared/src/generated/api.ts
```

---

## Decision 2: RTK Query with Custom baseQuery — No Dual HTTP Layer

### What

The web client uses **RTK Query for all API calls**, including multipart file uploads. A single custom `baseQuery` wrapper handles auth token injection and 401→refresh. No separate `ApiClient` class exists.

### Why

- Two HTTP layers means two places to set headers, two 401 handling paths, two patterns to learn
- RTK Query's `fetchBaseQuery` handles `FormData` bodies natively — file uploads don't need a separate client
- A custom `baseQuery` is ~50 lines and provides a single interception point for auth, error handling, and retry
- Desktop and mobile clients implement their own platform-specific HTTP layers (Electron `safeStorage`, Keychain/Keystore) — they don't benefit from a shared `ApiClient` abstraction

### Consequences

- **Positive**: One auth flow for the entire web client. Fix it once, works everywhere.
- **Positive**: RTK Query cache tags automatically invalidate on batch save (CSV upload → invalidates `Transaction` tag → dashboard and list refresh).
- **Positive**: No `ApiClient` → `TokenStorage` → `WebTokenStorage` abstraction chain to maintain.
- **Negative**: The 401→refresh logic in the custom `baseQuery` is web-only. Desktop and mobile reimplement it.

---

## Decision 3: Backend Aggregation Endpoint for Dashboard

### What

A new backend endpoint `GET /api/v1/transactions/summary` returns pre-computed category breakdowns, monthly trends, and summary stats using database `GROUP BY` queries. Supports pagination for historical browsing.

### Why

- Fetching all transactions for client-side aggregation downloads megabytes for users with 500+ transactions
- The backend can compute `SUM(amount) GROUP BY category` and `GROUP BY month` efficiently with existing indexes
- Returns ~3-5KB instead of potentially megabytes
- Pagination (`from`, `to`, `page`, `size`) lets the dashboard show "this month" by default and browse history

### Consequences

- **Positive**: Dashboard loads instantly even for users with years of data.
- **Positive**: Separates the dashboard data concern from the transaction list concern (different access patterns, different caching).
- **Negative**: Requires ~1 hour of backend work to add the endpoint, controller, and service method.
- **Mitigation**: Build dashboard last (Phase 7). Can use client-side aggregation as a temporary fallback if the endpoint isn't ready yet.

---

## Decision 4: Revised Build Order

### What

| Old Order (ADR-012) | New Order (ADR-013) |
|---------------------|---------------------|
| 1. Login | 1. Login |
| 2. Transaction List | **2. CSV Upload** (de-risk early) |
| 3. CSV Upload | 3. Transaction List |
| 4. Dashboard | 4. Dashboard |
| 5. OCR Receipt | 5. OCR Receipt |

### Why

CSV import is the app's primary value proposition — uploading bank statements and seeing parsed data. Building it second (right after auth) means the core file upload → parse → preview → save pipeline is tested and working early. If there are integration issues (multipart format, auth headers on file upload, content-type negotiation), they surface when only Login and Upload exist, not buried behind three other features.

### Consequences

- **Positive**: The riskiest integration point (file upload with auth) is validated early.
- **Positive**: CSV Upload and Transaction List can be built and tested together — upload data, then view it.
- **Negative**: The Upload page's OCR tab is built later (Phase 8), so the tab UI might need adjustment when the OCR tab is added.

---

## Related

- ADR-012: Web UI Implementation Plan (contains the full step-by-step plan)
- ADR-011: Frontend Monorepo with Multi-Client Architecture
- ADR-007: Vite + React + Tailwind + shadcn/ui Frontend
