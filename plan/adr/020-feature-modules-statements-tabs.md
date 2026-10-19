# ADR-020: Feature Modules, Monthly Statements, and Tabs

**Status:** Partially accepted — §4 (the module system) being implemented 2026-10-19 as the unblocker for
ADRs 023–025; §1 ArchUnit, §3 OCR refactor, §5 Statements and §6 Tabs remain proposed. Migration numbers in
this ADR (V10–V12) are superseded: V13/V14 were taken by ADR-021/022, so the module system lands at V15.
Household is now a first-class concept (ADR-022), not the deferral this ADR assumed.
**Date:** 2026-10-19
**Deciders:** Thai Ho
**Amends:** [ADR-010](./010-domain-module-packages.md) (adds a platform/feature dependency rule),
[ADR-018](./018-roles-permissions-features.md) (adds user-chosen modules on top of per-user features)
**Builds on:** [ADR-014](./014-local-tesseract-ocr.md) (OCR), [ADR-019](./019-budgets-monthly-overview.md) (months, category kinds)

## Context

Most of Kept is done. Two features come next:

1. A **monthly statement PDF** per user: a payslip-style summary page followed by a ledger of the month.
2. **Tabs**: one household member pays for other people's online-shopping deliveries (often COD) and needs to
   track and later show what each person owes.

Two problems in the current code stand in the way:

- **Capabilities are locked to the transactions domain.** `OcrService` reads a receipt *and* drafts a transaction:
  it imports `CategorizationService` and `TransactionIntake`, so Tabs cannot scan a COD slip without creating a
  transaction. `common.exception.GlobalExceptionHandler` imports `ocr` exceptions, so `common` depends on a domain.
  Running balances and PDF rendering don't exist yet; if built inside `transaction`, Tabs would copy them.
- **Everyone gets everything.** ADR-018 lets the super admin switch features per user, but a user can't choose.
  Some people only want Tabs, some only want spending tracking. `TRANSACTION_READ`/`WRITE` are `ROLE`
  permissions, so the spending side of the app cannot be turned off at all.

Tabs must not be built inside `transaction`: money paid for someone else is not your spending, and it would
inflate the overview, budgets and the statement.

## Decision

### 1. Platform and feature packages (amends ADR-010)

Packages stay top-level under `com.financetracker` (ADR-010) and in **one Gradle project**. Each package is either
**platform** (domain-agnostic, reusable) or a **feature** (a user-facing domain).

| Kind | Packages |
|---|---|
| Platform (existing) | `common`, `auth`, `access`, `settings` |
| Platform (changed) | `ocr`: reads a receipt, knows nothing about transactions |
| Platform (new) | `ledger`, `document`, `people` |
| Feature (existing) | `transaction`, `csv`, `category`, `budget`, `overview`, `admin` |
| Feature (new) | `statement`, `tab` |

**Dependency rules**, enforced by an ArchUnit test (`ArchitectureTest`) that runs with `./gradlew test`:

- Platform packages never import feature packages.
- New feature packages (`statement`, `tab`) import only platform packages and the published services listed below.
  Existing feature-to-feature imports (`admin → transaction`, `overview → transaction`, `csv → transaction.intake`)
  are recorded as allowed in the test, so the rule starts green and new edges must be added on purpose.
- `tab` never imports `transaction`, `category`, `budget` or `overview`.

### 2. Platform contracts

| Package | Contract | Notes |
|---|---|---|
| `ocr` | `ReceiptReader.read(MultipartFile) → ExtractedReceipt` | Decoding (image, first PDF page), `ReceiptOcrEngine`, `ReceiptTextParser`, `ExtractedReceipt`, `OcrException`. No categorization, no intake. |
| `ledger` | `LedgerCalculator.build(BigDecimal opening, List<LedgerEntry>) → Ledger` | `LedgerEntry(date, description, moneyIn, moneyOut, sortKey)`; `Ledger` adds the running balance per row, totals and closing balance. Pure Java, no DB. |
| `document` | `DocumentRenderer.renderPdf(String template, Object model, Locale locale) → byte[]` | Thymeleaf template → OpenHTMLtoPDF. Embeds a font with Vietnamese diacritics (Be Vietnam Pro). Shared CSS for multi-page tables. |
| `people` | `ContactService` (CRUD, scoped to the owner) | `Contact(id, ownerId, name, linkedUserId, archived)`. A contact needs no account. |

Running balances are computed in Java in `ledger`, not with SQL window functions, so every feature gets the same
ledger by mapping its rows to `LedgerEntry`.

### 3. OCR refactor

- Move decoding and reading out of `ocr.service.OcrService` into `ocr.ReceiptReader`.
- The transaction-specific part (`ExtractedReceipt → RawEntry → TransactionIntake.draft`, categorization) moves to
  `transaction.intake.ReceiptImport`. `POST /api/v1/ocr/...` keeps its URL and response; only the code behind it
  moves (controller moves to `transaction.api`).
- `OcrErrorCode` handling moves from `GlobalExceptionHandler` into an `@RestControllerAdvice` in `ocr`, so `common`
  no longer imports a domain.
- Metrics (`finance.ocr.*`) stay with the reader, tagged with the calling feature.

### 4. User-chosen modules (amends ADR-018)

A **module** is a user-facing part of Kept that a user can turn on or off for themselves. It groups permissions.

| Module | Permissions it covers |
|---|---|
| `SPENDING` | `TRANSACTION_READ`, `TRANSACTION_WRITE`, `DASHBOARD_VIEW`, `CSV_IMPORT`, `RECEIPT_SCAN`, `CATEGORY_READ`, `CATEGORY_WRITE`, `BUDGETS`, `STATEMENTS` |
| `TABS` | `TABS` |

Administration and monitoring permissions belong to no module and are never affected.

**Effective permissions** extend ADR-018:

```
SUPER_ADMIN       → every permission (unchanged)
anyone else       → role permissions
                    + features switched on for this user      (admin)
                    − features switched off for this user     (admin)
                    − permissions of modules the user turned off   (new: the user's choice)
disabled account  → nothing
```

- A user can only **narrow** what they have. Turning a module on never grants a permission the role, a plan or
  the admin didn't give.
- Turning a module off **hides and blocks**, it never deletes data. Turning it back on restores everything.
- At least one module must stay on.
- New accounts get every module on; registration asks "What do you want to use Kept for?" and saves the answer.
- `/auth/me` and the login response add `modules: [{ code, enabled, available }]`. Clients build navigation and the
  home screen from it: a Tabs-only user lands on the Tabs summary, not an empty overview.

### 5. Monthly statement (`statement`)

- **Page 1, summary:** income, expenses and net for the month, spending by expense category (categories under
  3 % of spending grouped into "Other"), and the change vs last month. Comes from `OverviewService`, so the
  ADR-019 rules apply unchanged: VND, Asia/Ho_Chi_Minh calendar months, counted by **category kind**, transfers
  excluded, refunds lower spending.
- **Pages 2+, ledger:** every income and expense transaction of the month, ordered by `(date, id)`, with money in,
  money out and running balance. Opening balance = net of all income and expense transactions before the month.
  Transfers are left out, as in the overview.
- **Header:** user, period, statement number (`YYYY-MM-NNNN`), issue date. Text in the user's language.
- **Snapshots:** the current month is always generated live and not stored. A statement for a closed month is
  generated once, stored, and served from storage afterwards, so editing an old transaction never changes a
  statement the user already downloaded. "Regenerate" makes a new statement with a new number.
- `GET /api/v1/statements/{YYYY-MM}.pdf`, permission `STATEMENTS` (new `FEATURE`, in the `SPENDING` module).

### 6. Tabs (`tab`)

- A **charge** is "I paid this for them"; a **repayment** is "they paid me back". Partial repayments need no
  matching to specific orders. Owed balance per contact = charges − repayments.
- Tab entries live in their own table, so they never appear in transactions, the overview, budgets or statements.
- Scanning a COD slip or receipt uses `ocr.ReceiptReader`, then the user picks a contact.
- A per-person statement (charges, repayments, outstanding balance) uses `ledger` and `document` with its own template.
- Permission `TABS` (new `FEATURE`, the `TABS` module), granted to `USER` and `ADMIN`.

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/tabs` | Contacts with outstanding balance, largest first, plus the total owed to you. |
| `GET` | `/api/v1/tabs/{contactId}/entries` | Entries with running balance. |
| `POST` | `/api/v1/tabs/entries` | `{ contactId, type, amount, date, description }` |
| `POST` | `/api/v1/tabs/scan` | Receipt or COD slip → prefilled entry (not saved). |
| `GET` | `/api/v1/tabs/{contactId}/statement.pdf?from=&to=` | Per-person statement. |
| `GET`/`POST`/`PATCH` | `/api/v1/contacts` | Contacts (`people`), usable by any feature. |

## Schema

```sql
-- V10__user_modules.sql
user_module_preferences (user_id FK users, module VARCHAR(30), enabled BOOLEAN, updated_at,
                         PRIMARY KEY (user_id, module))
-- role_permissions: grant STATEMENTS and TABS to USER and ADMIN

-- V11__statements.sql
statements (id, user_id FK, period DATE (day = 1), number VARCHAR(20), generated_at, pdf BYTEA,
            UNIQUE (user_id, number))
-- index (user_id, period)

-- V12__contacts_and_tabs.sql
contacts    (id, owner_user_id FK users, name VARCHAR(100), linked_user_id FK users NULL,
             archived BOOLEAN DEFAULT false, created_at)
tab_entries (id, owner_user_id FK users, contact_id FK contacts, type VARCHAR(20) -- CHARGE | REPAYMENT
             , amount DECIMAL(14,2) > 0, entry_date DATE, description VARCHAR(255), created_at)
-- index (owner_user_id, contact_id, entry_date)
```

Amounts use `DECIMAL(14,2)`, as `budgets` does. Statement PDFs are stored in the database until object storage
(MinIO) exists; one statement is around 100 KB.

## Order of work

1. **Foundation:** `ArchitectureTest`; OCR refactor (§3) with existing OCR tests green; modules (§4, V10),
   `/auth/me` change, module-aware navigation and home screen on web, desktop and mobile; module picker at
   registration and in settings.
2. **Statement:** `ledger` and `document`; `statement` (V11); download with month picker on web and desktop,
   share sheet on mobile.
3. **Tabs:** `people`; `tab` (V12); Tabs screens and home widget on all clients; per-person statement.

Feature work freezes after step 3; deployment (ADR-017) follows.

## Consequences

**Positive**

- OCR, ledgers and PDFs are written once and used by both Spending and Tabs, and by whatever comes next.
- A user sees only what they use, and data stays intact when they change their mind.
- Tabs never distort spending totals, budgets or statements, because it never writes transactions.
- Module boundaries are checked on every build instead of relying on discipline (ADR-010's main weakness).

**Negative**

- The OCR refactor touches working code before any new feature ships.
- Effective permissions need one more small query per request (module preferences); cache with the others if
  traces show it matters.
- Every client must build navigation from `modules` instead of assuming Spending exists.
- Statements stored as `BYTEA` grow the database; move them to object storage when MinIO is added.

## Alternatives considered

- **Tabs as a `contact_id` column on transactions.** Fastest, but owed money then counts as spending everywhere,
  and every total needs an extra filter that's easy to forget. Rejected.
- **Multi-tenancy.** Tenants isolate data between organizations; Kept's data is already scoped by `user_id`, and
  tenants don't decide which features a person sees. Rejected; a shared "household" can be added to Tabs later
  without it.
- **Gradle subprojects per module.** Compile-time enforcement, but checkstyle/PMD/SpotBugs, the Dockerfile and
  Flyway setup would all need splitting. ArchUnit gives the same rule with no build changes. Revisit if the rule
  is often bypassed.
- **Running balance in SQL (window functions).** Works for one query, but each feature would write its own.
  Rejected in favour of `ledger`.
- **JasperReports or iText for PDFs.** Heavier, harder to style, and iText's licence (AGPL) is a problem.
  HTML/CSS templates are easier to maintain.

## Open questions

- OpenHTMLtoPDF must be the line built on PDFBox 3 (the `io.github.openhtmltopdf` fork), since the backend already
  uses PDFBox 3.0.3. Confirm the version when adding it.
- Should a contact linked to a real Kept user (`linked_user_id`) see their own balance? Not in this ADR.
