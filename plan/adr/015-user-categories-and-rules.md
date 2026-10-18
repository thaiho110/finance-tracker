# ADR-015: User-Owned Categories and Categorization Rules

**Status:** Proposed  
**Date:** 2026-10-18  
**Deciders:** Thai Ho  
**Amends:** [ADR-005](./005-keyword-categorization.md) (Keyword + Regex Categorization)

## Context

Categories today are a side effect of the keyword table, not a feature:

- `category_mappings` (V3) is one global `keyword → category` table, seeded with mostly US merchants (Walmart, Kroger, Exxon…).
- `GET /api/v1/categories` returns `SELECT DISTINCT category` from that table. A category only exists if some keyword points to it.
- There is no API to create, rename or remove a category, or to add a keyword. The only way to change anything is a Flyway migration.
- `transactions.category` is a free-text `varchar`, so a rename would leave old rows behind and typos create "new" categories.
- `CategorizationService` loads the table once at `@PostConstruct`; new rows are ignored until restart.
- Matching bugs:
  - The Javadoc says the longest keyword wins, but the cache is never sorted.
  - `contains` matches inside words: `BP`, `GAS` and `PHO` hit `VEGAS`, `PHONG`, `BPO…`.

Users need to add their own categories (e.g. "Kids", "Pets", "Health") and teach the app their own merchants, which are mostly Vietnamese and not in the seed.

## Decision

Split categories and rules into two tables, each row owned either by the **system** (`user_id IS NULL`) or by **one user**. Users see the system defaults plus their own. System defaults are managed by an admin (ADR-016); everything else belongs to the user.

### Data model (V4 migration)

```sql
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NULL REFERENCES users(id) ON DELETE CASCADE,  -- NULL = system
    name        VARCHAR(100) NOT NULL,
    kind        VARCHAR(10)  NOT NULL DEFAULT 'EXPENSE' CHECK (kind IN ('EXPENSE', 'INCOME')),
    color       VARCHAR(20),
    icon        VARCHAR(40),
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
-- one name per owner; NULLS NOT DISTINCT makes system names unique too (PostgreSQL 15+)
CREATE UNIQUE INDEX uq_categories_owner_name ON categories (user_id, lower(name)) NULLS NOT DISTINCT;

CREATE TABLE category_rules (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NULL REFERENCES users(id) ON DELETE CASCADE,  -- NULL = system
    keyword      VARCHAR(255) NOT NULL,          -- stored normalized: upper case, no diacritics
    category_id  UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_rules_owner_keyword ON category_rules (user_id, keyword) NULLS NOT DISTINCT;

ALTER TABLE transactions ADD COLUMN category_id UUID REFERENCES categories(id);
```

Migration steps, in V4:

1. Insert each distinct `category_mappings.category` as a system category, plus `Other` and `Income`.
2. Copy `category_mappings` into `category_rules` as system rules.
3. Backfill `transactions.category_id` by name; unknown names become a **user** category for that transaction's owner, so no data is lost.
4. Add Vietnamese system categories and rules (see below).

V5, after the code no longer reads them: drop `transactions.category` and `category_mappings`.

`Other` is a system category that cannot be archived; it is the fallback for anything unmatched.

### Matching

`CategorizationService.categorize(userId, merchant)`:

1. Normalize the merchant: upper case, strip Vietnamese diacritics (`Normalizer.NFD` + remove marks, `Đ → D`), collapse whitespace.
2. Check the **user's rules**, then **system rules**.
3. Within each group, try **longer keywords first**.
4. Match on **word boundaries** (`(^|\W)KEYWORD(\W|$)`), not plain `contains`.
5. Skip rules whose category is archived. Fall back to `Other`.

Rules are small (hundreds per user), so the service queries them per request with a per-user cache evicted on any write. The startup-only cache is removed.

### Learning from corrections

When the user changes a category in the CSV/receipt review grid or the edit dialog, the UI offers:

> Always use **Coffee** for **HIGHLANDS COFFEE**?

Accepting creates a user rule (`keyword = cleaned merchant`). If a user rule for that keyword exists, it is updated. This is the main way the app gets better per user, with no admin involvement.

### Archiving instead of deleting

- A category with no transactions is deleted.
- A category with transactions is **archived**: hidden from pickers and rule matching, still shown on old transactions. Optionally the user picks a category to move those transactions to first.
- Users cannot edit or archive system categories, but can **hide** one for themselves (`user_hidden_categories (user_id, category_id)`).

### API (`/api/v1/categories`)

| Method | Path | Who | Notes |
|---|---|---|---|
| `GET` | `/categories` | user | System (minus hidden) + own, not archived. `?includeArchived=true` for settings |
| `POST` | `/categories` | user | `{ name, kind, color?, icon? }` |
| `PUT` | `/categories/{id}` | owner | Rename, recolor, archive |
| `DELETE` | `/categories/{id}?moveTo={id}` | owner | Archive if used; move transactions if `moveTo` given |
| `POST` / `DELETE` | `/categories/{id}/hide` | user | Hide/unhide a system category |
| `GET` | `/categories/rules` | user | Own rules; `?scope=system` for read-only defaults |
| `POST` | `/categories/rules` | user | `{ keyword, categoryId }`; upserts on keyword |
| `DELETE` | `/categories/rules/{id}` | owner | |

System categories and rules are edited through `/api/v1/admin/categories` (ADR-016).

Transaction DTOs carry `categoryId` plus `categoryName` for display. The `category` string field stays readable for one release so other clients don't break.

### UI

- Category select (review grid, edit dialog, filters): grouped list, colored dots, and a **"+ New category"** item at the bottom that opens a small inline form.
- "Always use…" prompt after a manual change.
- **Settings → Categories** page: list with rename, color, archive, hide (system); **Rules** tab with keyword → category, search, delete.

### Vietnamese seed (system)

Add at least: Highlands, Phúc Long, Katinat, Trung Nguyên, The Coffee House → Coffee; Circle K, GS25, FamilyMart, 7-Eleven, WinMart, Bách Hóa Xanh, Co.opmart, Lotte Mart, Aeon, MM Mega Market → Groceries; Shopee, Lazada, Tiki, TikTok Shop → Online Shopping; Grab, Be, Xanh SM, Petrolimex → Transportation; EVN, điện lực, cấp nước, Viettel, VNPT, FPT → Utilities; bệnh viện, phòng khám, nhà thuốc, Long Châu, Pharmacity → Health.

Remove or tighten short ambiguous system keywords (`BP`, `GAS`), which word-boundary matching makes safe but still noisy.

## Consequences

**Positive**

- Users can add categories and teach merchants without code changes or restarts.
- Categories are real rows with ids, so renames are safe and typos can't fork a category.
- Matching is deterministic (user > system, longest first, word boundaries) and testable.
- Fits the multi-client plan: any client uses the same endpoints.

**Negative**

- One more join on transaction queries (`category_id → categories`).
- The `category` string → `category_id` change touches DTOs, the web app and tests.
- Per-user rules mean the same merchant can land in different categories for different users. That is intended.

## Alternatives considered

- **Keep one global list, edited by an admin only.** Rejected: users can't add "Pets" without asking someone; a single shared taxonomy doesn't fit personal finance.
- **Free-text categories with autocomplete.** Rejected: no rename, typos create duplicates, nothing to attach colors or rules to.
- **ML-based categorization.** Rejected for now (same reasons as ADR-014: self-hosted, explainable, no training data). Rules learned from corrections cover most of the value.

## Test plan

- Matching: user rule beats system rule; longest keyword wins; `VEGAS` does not match `GAS`; diacritics (`PHÚC LONG` = `PHUC LONG`); archived category skipped; fallback `Other`.
- Ownership: a user cannot read, edit or attach rules to another user's category (404, not 403).
- Migration: run V4 against a DB with existing transactions; every row gets a `category_id`; unknown names become user categories.
- Archive/move: deleting a used category archives it; `moveTo` reassigns transactions.
