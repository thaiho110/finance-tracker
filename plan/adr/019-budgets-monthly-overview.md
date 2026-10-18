# ADR-019: Monthly Budgets and the Monthly Overview

**Status:** Accepted
**Date:** 2026-10-19
**Deciders:** Thai Ho
**Builds on:** [ADR-015](./015-user-categories-and-rules.md) (categories), [ADR-018](./018-roles-permissions-features.md) (permissions/features)

## Context

The dashboard so far shows all-time totals (`/api/v1/transactions/summary`). The app needs a **per-month**
view of money in and out, and a way to set a **monthly budget** per spending category and watch spending against it.
Decided in the grilling session on 2026-10-18.

## Decision

### Money and months

- **VND only.** Calendar months, counted in **Asia/Ho_Chi_Minh** time (the same zone receipt dates use).
- A transaction counts by its **category kind** (ADR-015, amended), not the sign of its amount: expense categories
  add to spending, income categories to income, **transfers to neither**. A refund is a negative amount in an
  expense category and so **lowers** that category's spending.

### Budgets

- Each expense category can have a **standing monthly amount**, plus an optional **total** budget across all
  spending (`category_id NULL`).
- A change is stored as one row with the month it starts from (`effective_from` = the first of the month). The
  budget in force for month *M* is the **newest row with `effective_from ≤ M`**. Past months keep their numbers.
  **Amount 0 ends** a budget from that month on. There is **no rollover** of unused money.
- Archiving a category (ADR-015) ends its budget from **next** month: `CategoryArchivedEvent` →
  `BudgetService.onCategoryArchived`. This month and earlier keep the budget they had.
- A budget may only be set on one of the user's **pickable expense** categories (not income, transfer, archived
  or hidden); the total budget has no category.

### Warnings

- Computed on the **server** so every client shows the same thing, as a status `OK | NEAR | OVER`:
  **amber (NEAR) at ≥ 80 %**, **red (OVER) at ≥ 100 %** of the budget in force. In-app only — no push, no email.
- Web shows a dashboard banner; mobile shows a badge on the Overview tab.

### The overview

One month (with a month picker): income, expenses and net; spending per expense category **vs its budget** and
**vs last month**; and the **top 5 merchants** (expense categories only). `GET /api/v1/overview?month=YYYY-MM`
(default: the current month). Budget fields are filled only when the user has `BUDGETS`; otherwise
`budgetsEnabled` is false and the budget/status fields are null.

### Permissions (ADR-018)

- **`BUDGETS`** is a **FEATURE** permission, granted to `USER` and `ADMIN` in `V6`; the super admin can switch it
  off per user.
- The overview endpoint needs **`DASHBOARD_VIEW`**; the budgets endpoints (`/api/v1/budgets/**`) need **`BUDGETS`**.

### API

| Method | Path | Permission | Notes |
|---|---|---|---|
| `GET` | `/api/v1/overview?month=YYYY-MM` | `DASHBOARD_VIEW` | Income/expenses/net, per-category spend vs budget and last month, top merchants. |
| `GET` | `/api/v1/budgets?month=YYYY-MM` | `BUDGETS` | The total plus every pickable expense category, each with its budget and spend this/last month. |
| `PUT` | `/api/v1/budgets` | `BUDGETS` | `{ categoryId?, amount?, month? }`; `categoryId` null = total, `amount` null/0 ends it, `month` null = this month. Returns the rows for that month. |

### Clients

- **Web/desktop:** the dashboard becomes the overview (keeps the month switcher, the stat cards now compare with
  last month, the budget banner, per-category progress bars, top merchants, and the monthly-trend chart from
  `/transactions/summary`). A separate **Budgets** page edits amounts — one row per expense category, the total at
  the top. `BUDGETS` is added to the admin roles page domain order.
- **Mobile:** a new **Overview** tab, first (gated by `DASHBOARD_VIEW`), with month prev/next, stats, banner and
  progress bars; tapping a bar edits that category's amount (gated by `BUDGETS`); the tab shows a badge when
  anything is NEAR or OVER.

## Schema (`V6__budgets.sql`)

```sql
budgets(id, user_id, category_id NULL, amount DECIMAL(14,2) ≥ 0, effective_from DATE (day = 1), created_at)
-- unique (user_id, category_id, effective_from) NULLS NOT DISTINCT; index (user_id, effective_from)
```

## Consequences

- Budget history is append-only: changing a budget never rewrites the past, so old overviews stay correct.
- Spending is summed as stored, so refunds net out naturally and transfers are ignored everywhere.
- Storing one row per change (not one per month) keeps the table small; the "newest ≤ M" lookup is a single
  indexed scan of a user's handful of rows.
