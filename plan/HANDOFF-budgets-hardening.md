# Handoff: budgets (item 2) and deploy hardening (item 3)

Written 2026-10-19 so the work can continue in a Claude Code session. Delete this file when both are merged.

## Where things are

| Branch | State |
|---|---|
| `feature/categories` | **Done** (phase 13, ADR-015 amended). Tests, Checkstyle, a real V1→V5 migration on data, and an API smoke test all passed. Ready for `./gradlew check` + PR. |
| `feature/budgets` | **Work in progress**, built on `feature/categories`. Only the backend start is in; see "Budgets: left to do". |
| `feature/deploy-hardening` | Not started. Branch it from `feature/budgets` (its integration tests cover budgets). |

Merge order: categories → budgets → deploy-hardening.

## Agreed decisions (grilling session 2026-10-18)

Categories (done): ADR-015 as written, plus everything in its "Amendments" section.

Budgets and monthly overview, to record as **ADR-019**:
- VND only. Calendar months, counted in Asia/Ho_Chi_Minh time.
- Each expense category has a standing monthly amount, plus an optional **total** budget (`category_id NULL`).
- Each change is stored with the month it starts from (`effective_from` = first day of the month). The budget for
  month M is the newest row with `effective_from <= M`. Past months keep their numbers. Amount 0 ends a budget.
  No rollover of unused money.
- Archiving a category ends its budget from **next** month (`CategoryArchivedEvent` → `BudgetService.onCategoryArchived`).
- `BUDGETS` is a **FEATURE** permission (ADR-018): granted to USER and ADMIN in V6, the super admin can switch it off
  per user. Overview itself needs `DASHBOARD_VIEW`; budget fields appear only with `BUDGETS`.
- Warnings in-app only: amber at ≥ 80 %, red at ≥ 100 % (status `OK | NEAR | OVER`, computed on the server).
  Dashboard banner on web, badge on the mobile Overview tab. No push/email.
- Overview for one month (month picker): income, expenses, net; spending per expense category vs its budget and vs
  last month; top 5 merchants (expense categories only). Transfers count nowhere; a negative amount in an expense
  category is a refund and lowers spending.
- Web/desktop: the dashboard becomes the overview (the current month switcher there doesn't filter anything — the
  old `/transactions/summary` is all-time); a separate **Budgets** page edits amounts (one row per expense category,
  total at the top). Mobile: new **Overview** tab, first; tapping a bar edits that category's amount.

Deploy hardening (item 3):
- Registration modes `OPEN | INVITE_ONLY | CLOSED` (ADR-016). Default `INVITE_ONLY` in production, `OPEN` in dev.
  Admin creates single-use invite links that expire after 7 days and sends them by hand (no SMTP). Mode is changed
  on an admin Settings page.
- Device API keys: stored hashed, created and revoked by an admin, shown once. Replaces `ApiKeyAuthFilter`, which
  today accepts **any** non-blank `X-Api-Key` as a device.
- Backups: a `backup` compose service running `pg_dump` nightly into `./backups`, keeping 14 days; `restore.sh`; a
  documented restore test. Off-site copy comes with deployment.
- Testcontainers integration tests in CI (migrations on existing data, category ownership 404s, budgets,
  registration modes).
- Swagger UI and `/v3/api-docs` only in the `dev` profile.
- Grafana anonymous admin → moved to the nginx phase (15).
- Also: a migration (V7 or later) that drops `transactions.category` and `category_mappings` (V5 kept them).

## Budgets: done so far (uncommitted work committed here as WIP)

- `V6__budgets.sql`: `budgets` table + `BUDGETS` granted to USER/ADMIN.
- `Permission.BUDGETS` (FEATURE), `PermissionDomain.BUDGETS`.
- `budget/model/Budget`, `budget/repository/BudgetRepository`, `budget/service/BudgetService`
  (`budgetsFor`, `set`, `onCategoryArchived`).
- `category/event/CategoryArchivedEvent`, published by `CategoryService` (update/delete→archive) and
  `SystemCategoryService` (update); `CategoryService.usableRef`.
- **Not compiled yet.** `CategoryServiceTest` needs an `ApplicationEventPublisher` mock added to its constructor call.

## Budgets: left to do

Backend
1. `overview` package: `OverviewService` + `GET /api/v1/overview?month=YYYY-MM` (default current month). Add native
   queries to `TransactionRepository`: totals per category for a date range (join `categories`, group by id, name,
   kind, color) and top merchants (expense kinds, group by `clean_merchant`, limit 5). Response sketch:
   `{ month, income, expenses, net, previousIncome, previousExpenses, budgetsEnabled, totalBudget: {amount, spent,
   status} | null, categories: [{categoryId, name, color, spent, previousSpent, budget, status}], topMerchants:
   [{merchant, total, count}] }`.
2. `budget/api/BudgetController`: `GET /api/v1/budgets?month=` (every pickable expense category + total, with amount,
   spent this month, spent last month) and `PUT /api/v1/budgets` `{ categoryId?, amount?, month? }`.
3. `SecurityConfig`: `/api/v1/overview/**` → `DASHBOARD_VIEW`; `/api/v1/budgets/**` → `BUDGETS` (before the
   admin deny-all rule).
4. Tests: `BudgetServiceTest` (newest row wins, past months unchanged, amount 0 ends, archived → ends next month,
   non-expense rejected), `OverviewServiceTest` (kinds, refunds, transfers excluded, statuses at 79/80/100 %).

Shared (`packages/shared`): types `Overview`, `BudgetRow`, `BudgetInput`; client `overview.get(month)`,
`budgets.list(month)`, `budgets.set(input)`; add `'BUDGETS'` to the `Permission` type.

Web: dashboard on the overview endpoint (keep the month switcher, stat cards with vs-last-month, budget banner,
category progress bars, top merchants; keep the monthly trend chart from `/transactions/summary`); `/budgets`
page + nav item (BUDGETS); add `'BUDGETS'` to `DOMAIN_ORDER` in `pages/admin/roles-page.tsx`.

Mobile: `OverviewScreen` as the first tab (gated by DASHBOARD_VIEW), month prev/next, stats, banner, progress bars,
tap to edit amount (BUDGETS), badge on the tab when anything is NEAR/OVER.

Docs: `plan/adr/019-budgets-monthly-overview.md`; `plan/README.md` phase list.

## How the work was verified (repeat this)

- Backend: `cd backend; ./gradlew check` on Windows. Note `config/pmd/pmd-ruleset.xml` references rules removed in
  PMD 7 (`EmptyTryBlock`, `EmptyFinallyBlock`, `EmptySwitchStatements`, …); if `pmdMain` fails to load the ruleset,
  replace them with `EmptyControlStatement` and `UnnecessarySemicolon`.
- Migrations against real data: start Postgres, apply V1–V4, insert a few users/transactions, then start the app
  (Flyway applies the rest) and check `categories`, `transactions.category_id`, `budgets`.
- Frontend: `pnpm --filter @finance-tracker/shared test`, `pnpm --filter @finance-tracker/web exec tsc -b`,
  `pnpm --filter @finance-tracker/mobile type-check`, `pnpm --filter @finance-tracker/mobile test`.

## Gotchas found on the way

- **Jackson 3 fails on a missing primitive** in a request record (`FAIL_ON_NULL_FOR_PRIMITIVES` is on). Use `Boolean`,
  not `boolean`, for optional flags (see `CategoryRuleRequest.applyToPast`).
- A 403 from a URL rule used to reach clients as 401 because `/error` needed a login; `/error` is now `permitAll`.
  Keep it that way, or the apps sign users out on "forbidden".
- `HandlerMethodValidationException` (validation of `List<@Valid …>` bodies) now maps to 400.
- Stored amounts are positive; the kind of the category decides income vs expense. The API accepts negative
  (refund) amounts; the web/mobile forms still enter positive ones.
- The repo checks files out with CRLF on Windows; generate patches/edits with LF and let git normalize.
- `backend/build/claude-tmp/` on Thai's machine holds temporary archives used to bring the Gradle cache into the
  cloud session; `./gradlew clean` removes them.
