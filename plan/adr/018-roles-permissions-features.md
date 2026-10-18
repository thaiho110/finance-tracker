# ADR-018: Role Table, Permissions per Domain, and Per-User Features

**Status:** Accepted  
**Date:** 2026-10-18  
**Deciders:** Thai Ho  
**Amends:** [ADR-016](./016-admin-area.md) (Admin Area): replaces its fixed `USER`/`ADMIN` roles

## Context

ADR-016 planned two hard-coded roles. The requirements are broader:

- Access to every domain of the app (transactions, dashboard, imports, categories, administration, monitoring) is decided by **one role table**, not by checks scattered through the code.
- Three roles to start: **USER**, **ADMIN**, **SUPER_ADMIN**. The super admin has every permission and manages users, admins and the rest of the application.
- The super admin decides **which features are on for which user**. Today this separates what users and admins can do; later it is how **paid features** get switched on.

## Decision

### Concepts

| Term | Meaning | Where it lives |
|---|---|---|
| **Permission** | One thing the app lets you do, in one domain (`CSV_IMPORT`, `USER_SUSPEND`). | Catalog in code (`Permission` enum): the code enforces it, so the code defines it. |
| **Role** | A named set of permissions with a rank. | `roles` + `role_permissions` tables, editable by the super admin. |
| **Feature** | A permission of kind `FEATURE` that can be switched on or off **per user**, overriding the role. | `user_feature_overrides` table. |

Each permission has a **kind**:

- `ROLE`: granted only through roles (e.g. `TRANSACTION_READ`, `USER_SUSPEND`).
- `FEATURE`: granted by roles **and** overridable per user (e.g. `CSV_IMPORT`, `RECEIPT_SCAN`, `DASHBOARD_VIEW`). Future paid features are added with this kind.
- `OWNER`: super admin only; cannot be put in any role (`ROLE_MANAGE`, `FEATURE_MANAGE`). This stops anyone handing out the power to hand out powers.

### Effective permissions

```
SUPER_ADMIN            → every permission in the catalog (not stored, not editable)
anyone else            → role permissions
                         + features switched on for this user
                         − features switched off for this user
disabled account       → nothing (requests get 401)
```

Computed when the user is loaded for a request and placed in Spring Security authorities as the permission names plus `ROLE_<code>`. A change by the super admin applies on the user's next request, without signing out.

### Catalog (first version)

| Domain | Permission | Kind | USER | ADMIN |
|---|---|---|---|---|
| Transactions | `TRANSACTION_READ` | ROLE | ✓ | ✓ |
| Transactions | `TRANSACTION_WRITE` | ROLE | ✓ | ✓ |
| Dashboard | `DASHBOARD_VIEW` | FEATURE | ✓ | ✓ |
| Imports | `CSV_IMPORT` | FEATURE | ✓ | ✓ |
| Imports | `RECEIPT_SCAN` | FEATURE | ✓ | ✓ |
| Categories | `CATEGORY_READ` | ROLE | ✓ | ✓ |
| Administration | `USER_READ` | ROLE | | ✓ |
| Administration | `USER_SUSPEND` | ROLE | | ✓ |
| Administration | `USER_CREATE` | ROLE | | |
| Administration | `AUDIT_READ` | ROLE | | ✓ |
| Monitoring | `MONITORING_VIEW` | ROLE | | ✓ |
| Administration | `ROLE_MANAGE` | OWNER | | |
| Administration | `FEATURE_MANAGE` | OWNER | | |

SUPER_ADMIN has all of them. The USER/ADMIN columns are the seeded defaults; the super admin can change them.

### Rules the code enforces

- **Rank:** USER 10, ADMIN 50, SUPER_ADMIN 100. You can only act on (suspend, change) someone of **lower** rank. The super admin can act on anyone except themselves.
- Nobody changes their **own** role or status.
- There is always at least one enabled SUPER_ADMIN.
- Creating a user with a role other than USER needs `ROLE_MANAGE`.
- Admin endpoints return account data only (email, role, status, counts), never amounts or transactions.
- Every administrative change writes an `audit_log` row (who, what, target, details, when).

### First super admin

`APP_SUPER_ADMIN_EMAIL`. Registering with that email creates a SUPER_ADMIN. If that user already exists, they are promoted at startup. No seeded account, no default password.

### Enforcement

One place: `SecurityConfig` maps each domain's URLs to a permission (`/api/v1/csv/** → CSV_IMPORT`, `GET /api/v1/transactions/** → TRANSACTION_READ`, `/api/v1/admin/users/*/role → ROLE_MANAGE`, …). Services add the rules URL patterns can't express (rank, self, last super admin). Clients get the user's permission list from login and `/auth/me` and use it only to show or hide UI.

### Data model (migration V4)

```sql
roles (code PK, name, rank, description, system)
role_permissions (role_code FK, permission, PK(role_code, permission))
users.role → FK roles(code); values normalized to upper case
users.enabled, users.last_login_at
user_feature_overrides (id, user_id FK, permission, enabled, updated_at, updated_by, UNIQUE(user_id, permission))
audit_log (id, actor_id FK, action, target, details, created_at)
```

### Path to paid features (not built now)

Add `plans` and `plan_features`, and `users.plan_code`. Effective permissions become `role + plan features ± user overrides`. Nothing above changes shape: a paid feature is a `FEATURE` permission that the USER role does not grant and a plan does.

### Monitoring access (with ADR-017)

Grafana is reached through nginx. nginx `auth_request` asks the backend whether the session has `MONITORING_VIEW`; Grafana runs in auth-proxy mode and maps SUPER_ADMIN to Grafana Admin and everyone else with the permission to Viewer. This needs the login to also set an HttpOnly session cookie scoped to the app's domain; built with the nginx work.

## Consequences

**Positive**

- One table decides access everywhere; a new domain adds catalog entries and URL rules in one place.
- Features per user work today and become paid plans later without a redesign.
- Disabling a user or switching off a feature takes effect on the next request.

**Negative**

- Two extra small queries when a user is loaded per request (role permissions, overrides). Cache later if traces show it.
- Permission names are strings in the database; renaming an enum constant needs a migration.

## Alternatives considered

- **Hard-coded roles with `hasRole` checks** (ADR-016 as written): no per-user features, every new rule is a code change.
- **Fully dynamic permissions in the database**: permissions only mean something where code checks them, so a DB-only permission would do nothing.
- **Separate feature-flag service** (Unleash, Flagsmith): another service to run; per-user entitlements are simple enough to keep in the app.
