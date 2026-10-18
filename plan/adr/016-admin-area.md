# ADR-016: Admin Area

**Status:** Proposed  
**Date:** 2026-10-18  
**Deciders:** Thai Ho  
**Related:** [ADR-009](./009-multi-client-api.md) (Multi-Client API), [ADR-015](./015-user-categories-and-rules.md) (Categories)

## Context

`users.role` exists (`admin | user`) but nothing uses it: every registration gets `user`, no endpoint checks the role, and there is no way to manage the application without SQL. Things that need an owner:

- **System defaults** from ADR-015 (system categories and rules). Without an admin they can only change through migrations.
- **Users**: disable an account, reset a role, remove a user and their data.
- **Registration**: a self-hosted instance exposed through nginx (ADR-017) should not let anyone on the network sign up.
- **Device API keys** (ADR-009). `ApiKeyAuthFilter` currently authenticates **any non-blank `X-Api-Key`** as `ROLE_DEVICE`. There is no key store, so any string works. This is a security bug that must be fixed before the app is reachable by URL.
- **Operational status**: is OCR ready, which version is running, where are the dashboards.

## Decision

Add an **ADMIN role** and an **admin area** in the web app at `/admin`, backed by `/api/v1/admin/**` endpoints that require `ROLE_ADMIN`.

### Privacy boundary

Admins manage the *application*, not users' *money*. Admin endpoints never return transactions, receipt items, amounts or user-owned categories and rules. User management shows counts only (e.g. number of transactions), never contents. No impersonation.

### Roles and authentication

- Roles: `USER`, `ADMIN` (stored upper case; V6 migration normalizes existing `user`/`admin`). Spring authorities `ROLE_USER` / `ROLE_ADMIN`.
- The JWT carries a `role` claim; `/auth/me` returns it; the web app uses it only to show or hide the Admin nav. The backend always enforces.
- `users.enabled BOOLEAN NOT NULL DEFAULT TRUE`. `JwtAuthenticationFilter` loads the user per request (already needed for the user id) and rejects disabled users, so disabling takes effect immediately rather than when the token expires.
- Fix the user id: `CsvController`, `OcrController` and `TransactionController` derive it with `UUID.nameUUIDFromBytes(email)`, but `users.id` is a random UUID (`GenerationType.UUID`), and `transactions.created_by` has a foreign key to `users(id)`. Saved transactions therefore point at an id that does not exist in `users` (FK violation on insert). Use the real id from the loaded user (a `CurrentUser` argument resolver); this is needed before any admin feature that counts or deletes a user's data, and it stops an email change from orphaning data.

### Bootstrapping the first admin

`APP_ADMIN_EMAIL` (env var). On startup, if a user with that email exists and is not admin, promote them and log it. If no user exists yet, the first registration with that email becomes admin. No default password, no seeded admin account.

The app also refuses to demote or disable the **last** admin.

### Features (v1)

| Area | What the admin can do |
|---|---|
| **Users** | List (email, role, enabled, created, last login, transaction count); disable/enable; change role; delete user and all their data (typed-email confirmation) |
| **Registration** | Mode: `OPEN`, `INVITE_ONLY`, `CLOSED`. Invite-only: create single-use invite links with expiry |
| **System categories & rules** | CRUD on `user_id IS NULL` rows from ADR-015 (add Vietnamese merchants, fix bad keywords) |
| **Device API keys** | Create (name, owner user, rate limit); the key is shown **once**; list with prefix + last used; revoke |
| **Settings** | Registration mode, default currency (VND), OCR languages (read-only display for now) |
| **Status** | App version + git SHA, DB reachable, Tesseract version and languages, Flyway version, link to Grafana |
| **Audit log** | Every admin action: who, what, target, when. Read-only list with filter |

### Device API keys (replaces the current filter behaviour)

```sql
CREATE TABLE api_keys (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,  -- data written by the device belongs to this user
    key_prefix   VARCHAR(12)  NOT NULL,          -- shown in UI, e.g. "ftk_3fa9c1"
    key_hash     VARCHAR(64)  NOT NULL UNIQUE,   -- SHA-256 of the full key
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP,
    revoked_at   TIMESTAMP
);
```

- Format `ftk_` + 32 random bytes (Base64URL). Generated with `SecureRandom`.
- SHA-256 is enough (high-entropy random keys, not passwords); lookup by hash.
- `ApiKeyAuthFilter`: hash the header, look up an active key, authenticate as the owning user with `ROLE_DEVICE`; unknown or revoked key → 401. `last_used_at` updated at most once a minute.
- Until this ships, the filter is disabled (it only adds risk; no device client exists yet).

### Other tables

```sql
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP;

CREATE TABLE app_settings (key VARCHAR(100) PRIMARY KEY, value TEXT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT NOW());

CREATE TABLE invites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(64) NOT NULL UNIQUE, email VARCHAR(255),
    expires_at TIMESTAMP NOT NULL, used_at TIMESTAMP,
    created_by UUID NOT NULL REFERENCES users(id)
);

CREATE TABLE admin_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,        -- USER_DISABLED, ROLE_CHANGED, API_KEY_CREATED, ...
    target VARCHAR(255), details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Code layout

New `admin` domain module (ADR-010): `admin/api`, `admin/service`. It calls the public services of `auth` and `category`; it does not reach into their repositories. `SecurityConfig`: `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")` plus `@PreAuthorize` on the services as a second check.

### Web

- `/admin` route with `AdminGuard`; sidebar item visible only when `role === 'ADMIN'`.
- Pages: Users, Registration & invites, System categories, API keys, Settings, Status, Audit log. Same design system as the rest of the app.

### Also tightened while doing this

- `/h2-console/**` permit rule removed (no H2 in the app).
- Swagger UI and `/v3/api-docs` only in the `dev` profile.
- Grafana stops allowing anonymous Admin (`GF_AUTH_ANONYMOUS_ORG_ROLE: Admin`) once it is reachable by URL (ADR-017); it gets its own admin password from `.env`.

## Consequences

**Positive**

- The app can be run and maintained without SQL.
- Closes the API key hole and gives a safe way to onboard device clients.
- Registration control makes it safe to put the app on a URL.

**Negative**

- More surface to test and secure; every admin endpoint is a privilege boundary.
- Loading the user per request adds a DB lookup (cheap; can be cached briefly if it shows up in traces).

## Alternatives considered

- **Separate admin app or Spring Boot Admin.** Rejected: another service to run and secure for a handful of screens.
- **Manage everything through SQL and migrations.** Rejected: fine for one developer, not for a URL-accessible instance.
- **Admins can view all data.** Rejected: conflicts with the privacy principle; not needed for administration.

## Test plan

- Every `/api/v1/admin/**` endpoint returns 403 for `ROLE_USER` and 401 anonymous (parameterized test over the mappings).
- Disabled user's existing JWT gets 401 on the next request.
- Last admin cannot be demoted or disabled.
- API keys: random string → 401; valid key → authenticated as owner; revoked → 401; key never returned after creation.
- Registration modes: `CLOSED` rejects, `INVITE_ONLY` accepts a valid unused unexpired invite once.
- Admin responses contain no transaction fields (JSON schema assertion).
- Each admin action writes one audit row.
