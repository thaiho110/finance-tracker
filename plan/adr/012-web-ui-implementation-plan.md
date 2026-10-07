# ADR-012: Web UI Implementation Plan

**Status:** Draft  
**Date:** 2026-10-07  
**Deciders:** Thai Ho

## Context

The backend (Spring Boot 4.1, REST API at `/api/v1/`) is fully built and tested. Per ADR-011, the frontend uses a pnpm + Turborepo monorepo with a shared types package and a Vite + React web client. This ADR details the step-by-step implementation plan for Phase 9 (shared package) and Phase 10 (web app) from the architecture plan.

## Backend API Reference (Exact Contract)

All endpoints are at `/api/v1/...` with `X-Client-Id: finance-tracker-web` header.

### Auth Endpoints

| Method | Path | Request | Response |
|--------|------|---------|----------|
| POST | `/api/v1/auth/register` | `{ email: string, password: string }` | `{ token, refreshToken, email, role }` |
| POST | `/api/v1/auth/login` | `{ email: string, password: string }` | `{ token, refreshToken, email, role }` |
| POST | `/api/v1/auth/refresh` | Bearer token in Authorization header | `{ token, refreshToken, email, role }` |

### Transaction Endpoints

| Method | Path | Request / Params | Response |
|--------|------|-----------------|----------|
| GET | `/api/v1/transactions` | Query: `page`, `size`, `sort`, `category`, `dateFrom`, `dateTo` | `Page<TransactionResponse>` |
| GET | `/api/v1/transactions/{id}` | Path: `id` (UUID) | `TransactionResponse` |
| PUT | `/api/v1/transactions/{id}` | Path: `id`, Body: `TransactionRequest` | `TransactionResponse` |
| DELETE | `/api/v1/transactions/{id}` | Path: `id` (UUID) | `204 No Content` |
| POST | `/api/v1/transactions/batch` | `TransactionRequest[]` | `TransactionResponse[]` |

### CSV & OCR Endpoints

| Method | Path | Request | Response |
|--------|------|---------|----------|
| POST | `/api/v1/csv/parse` | Multipart: `file` | `ParsedTransactionResponse[]` |
| POST | `/api/v1/ocr/process` | Multipart: `image` | `ParsedReceiptResponse` |

### Categories

| Method | Path | Response |
|--------|------|----------|
| GET | `/api/v1/categories` | `string[]` |
| GET | `/api/v1/categories/mappings` | `CategoryMapping[]` |

### DTO Shapes (Exact Field Names)

```
TransactionRequest { date, rawDescription, cleanMerchant, amount, category, sourceType }
TransactionResponse { id, date, rawDescription, cleanMerchant, amount, category, sourceType, clientId, isDuplicate, createdAt, receiptItems? }
  ReceiptItemResponse { id, itemDescription, price }
ParsedTransactionResponse { rowIndex, date, rawDescription, cleanMerchant, amount, category, isDuplicate }
ParsedReceiptResponse { merchant, date, totalAmount, category, isDuplicate, lineItems[] }
  LineItem { item, price }
AuthResponse { token, refreshToken, email, role }
AuthRequest { email, password }
RegisterRequest { email, password }
```

---

## Changes from Grilling Session (2026-10-07)

The following decisions were refined during a structured design review:

| # | Topic | Original (ADR-012) | Refined (ADR-013) |
|---|-------|--------------------|--------------------|
| Q1 | HTTP Layer | Dual: `ApiClient` (shared) + RTK Query (web) | **Clarified**: RTK Query with custom `baseQuery` for web; platform-specific HTTP clients for desktop/mobile. `ApiClient` removed from shared package. |
| Q2 | State Management | Redux Toolkit + RTK Query | **Confirmed**: Keep RTK Query |
| Q3 | Token Refresh | In `ApiClient.fetchWithAuth()` | **Moved**: Custom RTK Query `baseQuery` handles 401→refresh for web. Each platform's HTTP layer handles its own refresh. |
| Q4 | Shared Package Scope | Types + ApiClient + Validation | **Narrowed**: Types only (auto-generated from OpenAPI spec). Validation stays in web package. ApiClient removed. |
| Q4b | Type Generation | Manual TypeScript types | **Auto-generated**: `openapi-typescript` from SpringDoc `/v3/api-docs` |
| Q5 | Dashboard Data | Client-side aggregation | **Backend endpoint**: `GET /api/v1/transactions/summary` with pagination |
| Q6 | Build Order | Login → Transaction List → CSV → OCR → Dashboard | **Revised**: Login → **CSV Upload** → Transaction List → Dashboard → OCR |

---

## Implementation Plan

### Phase 0: Prerequisites & Tooling

**Goal:** Install tooling and configure the monorepo scaffold.

| Step | Action | Details |
|------|--------|---------|
| 0.1 | **Install pnpm** | `npm install -g pnpm` — required for workspace management |
| 0.2 | **Create root config files** | `package.json`, `pnpm-workspace.yaml`, `turbo.json`, root `tsconfig.json` |
| 0.3 | **Update `.gitignore`** | Add `node_modules/`, `dist/`, `.turbo/`, `packages/*/dist/` patterns |
| 0.4 | **Create `packages/` directory** | Scaffold `shared/`, `web/` directories |

**Root `package.json` fields:** name `"finance-tracker"`, private `true`, scripts for `dev`, `build`, `lint`, `type-check`.  
**`pnpm-workspace.yaml`:** packages at `packages/*`.  
**`turbo.json`:** Pipeline for `build`, `lint`, `type-check` with dependency graph awareness.

---

### Phase 1: Shared Package (`@finance-tracker/shared`)

**Goal:** Auto-generated TypeScript types consumed by all clients. Pure interfaces only — no runtime code, no platform dependencies.

#### Step 1.1: Package Setup

| File | Purpose |
|------|---------|
| `packages/shared/package.json` | Name `@finance-tracker/shared`, exports only `./types`, no dependencies |
| `packages/shared/tsconfig.json` | Strict TS, composite, declaration output |
| `packages/shared/scripts/generate-types.sh` | Fetch OpenAPI spec and run `openapi-typescript` |

#### Step 1.2: Auto-Generated Types via `openapi-typescript`

The backend already has **SpringDoc OpenAPI** with `@Schema` annotations on every DTO and controller. The OpenAPI spec is served at `/v3/api-docs` (already permitted in `SecurityConfig`).

**Generation flow:**
```bash
# 1. Start the backend (must be running)
# 2. Run the type generator
npx openapi-typescript http://localhost:8080/v3/api-docs \
  --output packages/shared/src/generated/api.ts
```

**What gets generated:**
- All DTO interfaces: `TransactionRequest`, `TransactionResponse`, `AuthRequest`, `AuthResponse`, `ParsedTransactionResponse`, `ParsedReceiptResponse`, etc.
- The `Page<T>` generic wrapper
- Enums (`SourceType`)
- Validation constraints as JSDoc comments (`@minLength`, `@maximum`, etc.)
- Proper nullability from `@Schema` annotations

**Source of truth:** Backend Java DTOs → SpringDoc annotations → OpenAPI spec → TypeScript interfaces. When a DTO changes in Java, the generated types break the frontend build immediately — catching API drift at compile time.

#### Step 1.3: Add a pnpm workspace script

```json
// root package.json
{
  "scripts": {
    "gen:types": "openapi-typescript http://localhost:8080/v3/api-docs -o packages/shared/src/generated/api.ts"
  }
}
```

Run `pnpm gen:types` after any backend DTO change, and before frontend builds.

#### Step 1.4: Package Entry Point

```typescript
// packages/shared/src/index.ts
export type * from './generated/api';
```

Nothing else lives in shared. No `ApiClient`, no `TokenStorage`, no validation logic. Each platform owns its HTTP layer and validation.

#### Step 1.5: Build

- `tsc` builds `generated/api.ts` to `dist/` with declarations
- No runtime dependencies — pure interfaces are tree-shakeable

---

### Phase 2: Web App Foundation

**Goal:** Scaffold Vite + React + TypeScript app with Tailwind, shadcn/ui, routing, and Redux Toolkit.

#### Step 2.1: Scaffold Vite Project

| File | Purpose |
|------|---------|
| `packages/web/package.json` | Dependencies: react, react-dom, react-router-dom, @reduxjs/toolkit, react-redux, tailwindcss, lucide-react, @tanstack/react-table, react-dropzone, recharts, @radix-ui/*, class-variance-authority, clsx, tailwind-merge |
| `packages/web/vite.config.ts` | React plugin, path aliases (`@/` → `src/`), proxy `/api/*` → `http://localhost:8080` |
| `packages/web/tsconfig.json` | Path aliases, strict mode, JSX react-jsx |
| `packages/web/tsconfig.node.json` | Node config for vite.config.ts |
| `packages/web/index.html` | SPA entry point |
| `packages/web/src/main.tsx` | React root with Redux Provider + BrowserRouter |
| `packages/web/src/App.tsx` | Route definitions |

#### Step 2.2: Tailwind CSS v4 + shadcn/ui Setup

| Step | Action |
|------|--------|
| 2.2.1 | Create `packages/web/src/index.css` with Tailwind directives |
| 2.2.2 | Create `packages/web/src/lib/utils.ts` — `cn()` helper (clsx + tailwind-merge) |
| 2.2.3 | Initialize shadcn/ui: `npx shadcn@latest init` (choose New York style, neutral color, CSS variables) |
| 2.2.4 | Add core shadcn/ui components: `button`, `input`, `label`, `card`, `dialog`, `dropdown-menu`, `table`, `badge`, `select`, `form`, `toast`, `skeleton`, `avatar`, `separator`, `pagination` |

#### Step 2.3: Redux Store Setup

| File | Contents |
|------|----------|
| `packages/web/src/store/index.ts` | `configureStore()` with slices |
| `packages/web/src/store/auth-slice.ts` | Auth state: `{ user, token, refreshToken, isAuthenticated, isLoading }` with `login`, `logout`, `setCredentials`, `setLoading` reducers |
| `packages/web/src/store/api-slice.ts` | RTK Query `createApi` with `fetchBaseQuery` for `/api/v1/*`, tag types for `Transaction`, `Category` |
| `packages/web/src/store/api/` | RTK Query endpoint injections: `auth-api.ts`, `transaction-api.ts`, `csv-api.ts`, `ocr-api.ts`, `category-api.ts` |

#### Step 2.4: Custom RTK Query baseQuery for Auth

RTK Query handles **all** API calls for the web client, including file uploads. A custom `baseQuery` wrapper handles auth token injection and 401→refresh:

| File | Contents |
|------|----------|
| `packages/web/src/lib/base-query.ts` | Custom `baseQuery` that: injects `Authorization: Bearer <token>` + `X-Client-Id: finance-tracker-web` headers, handles 401 by attempting token refresh via `POST /api/v1/auth/refresh`, retries the original request on success, redirects to `/login` on failure |
| `packages/web/src/lib/token-storage.ts` | Token persistence via `localStorage` — `getToken()`, `setToken()`, `getRefreshToken()`, `setRefreshToken()`, `clear()` |

**Multipart uploads with RTK Query:**
```typescript
// RTK Query handles file uploads via formData body
parseCsv: builder.mutation<ParsedTransactionResponse[], FormData>({
  query: (formData) => ({
    url: '/api/v1/csv/parse',
    method: 'POST',
    body: formData,
    // Don't set Content-Type — browser sets multipart/form-data with boundary
  }),
});
```

No separate `ApiClient` class. No dual HTTP layer. One custom `baseQuery` for everything.

#### Step 2.5: Route Structure

```
/             → redirect to /dashboard if authenticated, /login if not
/login        → LoginPage (public)
/register     → RegisterPage (public)
/dashboard    → DashboardPage (protected)
/transactions → TransactionListPage (protected)
/transactions/:id → TransactionDetailPage (protected)
/upload       → UploadPage (protected, CSV + OCR)
```

| File | Contents |
|------|----------|
| `packages/web/src/router.tsx` | Route definitions with `createBrowserRouter` |
| `packages/web/src/components/guards/auth-guard.tsx` | `ProtectedRoute` component — redirects to `/login` if not authenticated |
| `packages/web/src/layouts/app-layout.tsx` | Authenticated layout with sidebar/nav + main content area |
| `packages/web/src/layouts/auth-layout.tsx` | Minimal centered layout for login/register pages |

#### Step 2.6: Design System Generation

Use the ui-ux-pro-max skill to generate a design system:

```bash
python .opencode/skills/ui-ux-pro-max/scripts/search.py "personal finance tracker tool dashboard" --design-system -p "Finance Tracker" -f markdown
```

Apply the output as CSS variables in `index.css` and Tailwind theme extension.

---

### Phase 3: Auth Pages (Login + Register)

**Goal:** Functional authentication flow with JWT storage and protected routes.

#### Step 3.1: Login Page

| Component | File | Details |
|-----------|------|---------|
| Login form | `packages/web/src/pages/login-page.tsx` | Email + password inputs, submit button, link to register |
| Auth card | `packages/web/src/components/auth/auth-card.tsx` | Centered card with logo, title, form slot |
| Form validation | Inline validation with error messages per field |

**Flow:**
1. User enters email + password → calls `POST /api/v1/auth/login`
2. On success: store `token` + `refreshToken` in localStorage, dispatch `setCredentials`, redirect to `/dashboard`
3. On error: show error message (invalid credentials, server error)
4. On mount: if already authenticated, redirect to `/dashboard`

#### Step 3.2: Register Page

| Component | File | Details |
|-----------|------|---------|
| Register form | `packages/web/src/pages/register-page.tsx` | Email + password + confirm password, submit button, link to login |
| Password validation | Min 8 characters, confirm match |

**Flow:**
1. User enters email + password → calls `POST /api/v1/auth/register`
2. On success: auto-login (store tokens, redirect to `/dashboard`)
3. On error: show error message (email taken, validation errors)

#### Step 3.3: Auth Slice (RTK Query)

```typescript
// packages/web/src/store/api/auth-api.ts
const authApi = api.injectEndpoints({
  endpoints: (builder) => ({
    login: builder.mutation<AuthResponse, AuthRequest>({...}),
    register: builder.mutation<AuthResponse, RegisterRequest>({...}),
    refreshToken: builder.mutation<AuthResponse, void>({...}),
  }),
});
```

#### Step 3.4: Auth Guard Component

```typescript
// packages/web/src/components/guards/auth-guard.tsx
// Checks redux auth state + localStorage token
// Redirects to /login if not authenticated
// Shows loading skeleton while checking
```

#### Step 3.5: Logout Flow

- Navbar user menu with "Logout" option
- Clears localStorage tokens, dispatches `logout`, redirects to `/login`

---

### Phase 4: App Layout

**Goal:** Build the authenticated app shell.

#### Step 4.1: App Layout

| File | Contents |
|------|----------|
| `packages/web/src/layouts/app-layout.tsx` | Sidebar nav (collapsible on mobile) + top bar (user menu) + main content |

**Sidebar navigation items:**
- Dashboard (layout-dashboard icon)
- Transactions (wallet / arrow-left-right icon)
- Upload CSV (upload icon)
- OCR Receipt (camera / scan icon)

**Top bar:**
- Page title (dynamic)
- User avatar + email dropdown (Logout)

#### Step 4.2: Responsive Behavior

- Desktop: sidebar visible, 3-column layout with nav
- Tablet: collapsed sidebar, hamburger toggle
- Mobile: bottom tab bar or off-canvas sidebar

---

### Phase 5: CSV Upload Page

**Goal:** Drag-and-drop CSV upload with preview grid and batch save. This is the primary feature of the app — build it early to de-risk the file upload → parse → preview → save pipeline.

#### Step 5.1: Page Structure

| Component | File | Details |
|-----------|------|---------|
| Upload page | `packages/web/src/pages/upload-page.tsx` | Tabbed: CSV Upload / OCR Receipt |
| CSV dropzone | `packages/web/src/components/csv/csv-dropzone.tsx` | react-dropzone area with file type validation (.csv) |
| Parsing state | Loading skeleton while `POST /api/v1/csv/parse` runs |
| Preview grid | `packages/web/src/components/csv/preview-grid.tsx` | Editable table of parsed transactions |
| Confirm save | `packages/web/src/components/csv/save-bar.tsx` | "Save X transactions" button with count |

#### Step 5.2: Flow

1. User drops/selects a `.csv` file
2. File is uploaded via `POST /api/v1/csv/parse` (multipart)
3. Response: `ParsedTransactionResponse[]` — displayed in editable preview grid
4. User can edit any field before saving
5. User clicks "Save All" → `POST /api/v1/transactions/batch` with the (possibly edited) transactions
6. On success: toast notification, option to view saved transactions or upload another

#### Step 5.3: Preview Grid

- Uses `@tanstack/react-table`
- Each row: rowIndex, date (datepicker), description (text), merchant (text), amount (number), category (dropdown)
- Duplicate rows highlighted with warning icon
- Row selection for batch actions
- "Remove row" button per row

#### Step 5.4: RTK Query Endpoints

```typescript
parseCsv: builder.mutation<ParsedTransactionResponse[], FormData>({
  query: (formData) => ({
    url: '/api/v1/csv/parse',
    method: 'POST',
    body: formData,
  }),
});

batchSave: builder.mutation<TransactionResponse[], TransactionRequest[]>({
  query: (transactions) => ({
    url: '/api/v1/transactions/batch',
    method: 'POST',
    body: transactions,
  }),
  invalidatesTags: ['Transaction'],  // invalidate list and dashboard
});
```

---

### Phase 6: Transaction List Page

**Goal:** Paginated, filterable, sortable transaction table.

#### Step 5.1: Page Structure

| Component | File | Details |
|-----------|------|---------|
| Transaction list page | `packages/web/src/pages/transactions-page.tsx` | Filters bar + table + pagination |
| Filters bar | `packages/web/src/components/transactions/filters-bar.tsx` | Category dropdown, date range picker, search |
| Transactions table | `packages/web/src/components/transactions/transactions-table.tsx` | @tanstack/react-table with sortable columns |
| Transaction row | `packages/web/src/components/transactions/transaction-row.tsx` | Row with action buttons (edit, delete) |
| Pagination | `packages/web/src/components/transactions/pagination-bar.tsx` | Page controls using shadcn pagination |

#### Step 5.2: Table Columns

| Column | Accessor | Sortable | Format |
|--------|----------|----------|--------|
| Date | `date` | Yes | `YYYY-MM-DD` |
| Merchant | `cleanMerchant` | Yes | Text |
| Description | `rawDescription` | No | Truncated with tooltip |
| Amount | `amount` | Yes | Currency format `$X,XXX.XX` |
| Category | `category` | Yes | Badge |
| Source | `sourceType` | Yes | Badge (CSV/OCR/Manual) |
| Duplicate | `isDuplicate` | Yes | Badge or icon |
| Actions | — | No | Edit / Delete buttons |

#### Step 5.3: RTK Query Integration

```typescript
// packages/web/src/store/api/transaction-api.ts
const transactionApi = api.injectEndpoints({
  endpoints: (builder) => ({
    getTransactions: builder.query<Page<TransactionResponse>, TransactionsQueryParams>({...}),
    getTransaction: builder.query<TransactionResponse, string>({...}),
    updateTransaction: builder.mutation<TransactionResponse, { id: string; data: TransactionRequest }>({...}),
    deleteTransaction: builder.mutation<void, string>({...}),
    batchSaveTransactions: builder.mutation<TransactionResponse[], TransactionRequest[]>({...}),
  }),
});
```

#### Step 5.4: Transaction Detail / Edit

| Component | File | Details |
|-----------|------|---------|
| Transaction detail | `packages/web/src/pages/transaction-detail-page.tsx` | Full transaction view with edit capability |
| Edit dialog | `packages/web/src/components/transactions/edit-transaction-dialog.tsx` | Modal form pre-filled with transaction data |

#### Step 5.5: Delete Confirmation

| Component | File | Details |
|-----------|------|---------|
| Delete dialog | Reusable confirmation dialog | "Are you sure?" with transaction details |

---

### Phase 7: Dashboard Page

**Goal:** Overview with summary stats, category breakdown chart, and monthly trends. Uses a dedicated backend aggregation endpoint (avoids client-side computation over large datasets).

#### Step 7.1: Backend Aggregation Endpoint

Add a new backend endpoint (estimate: ~1 hour backend work):

| Method | Path | Query Params | Response |
|--------|------|-------------|----------|
| GET | `/api/v1/transactions/summary` | `page?`, `size?`, `from?`, `to?` | `TransactionSummaryResponse` |

```typescript
// Response shape
interface TransactionSummaryResponse {
  totalExpenses: number;
  totalIncome: number;
  transactionCount: number;
  averageTransaction: number;
  categoryBreakdown: Array<{ category: string; total: number; count: number }>;
  monthlyTrend: Array<{ month: string; total: number; count: number }>;
  recentTransactions: TransactionResponse[];  // last 10
}
```

The backend uses a single query with `GROUP BY` — efficient, indexed, and returns ~3-5KB instead of potentially megabytes of raw transactions.

#### Step 7.2: Page Structure

| Component | File | Details |
|-----------|------|---------|
| Dashboard page | `packages/web/src/pages/dashboard-page.tsx` | Summary cards + charts + recent list |
| Summary cards | `packages/web/src/components/dashboard/summary-cards.tsx` | Total expenses, total income, transaction count, this month |
| Category pie chart | `packages/web/src/components/dashboard/category-chart.tsx` | recharts PieChart with legend |
| Monthly trend | `packages/web/src/components/dashboard/monthly-trend.tsx` | recharts BarChart or AreaChart |
| Recent transactions | `packages/web/src/components/dashboard/recent-transactions.tsx` | Last 10 transactions list |

#### Step 7.3: Chart Library (recharts)

```bash
pnpm add recharts --filter @finance-tracker/web
```

| Chart | Type | Data Source |
|-------|------|-------------|
| Category breakdown | `PieChart` | `categoryBreakdown` from summary endpoint |
| Monthly spending | `BarChart` | `monthlyTrend` from summary endpoint |
| (Future) Daily trend | `AreaChart` | Future: add `dailyTrend` to summary endpoint |

#### Step 7.4: Pagination for Large Datasets

The summary endpoint supports pagination parameters (`page`, `size`, `from`, `to`) so the dashboard can:
- Show "this month" by default (`from=2026-10-01&to=2026-10-31`)
- Allow date range selection to view specific periods
- Handle users with thousands of transactions across years
- Paginate through historical summary data

---

### Phase 8: OCR Receipt Upload

**Goal:** Upload receipt images and preview parsed data.

#### Step 8.1: Page Structure

| Component | File | Details |
|-----------|------|---------|
| OCR dropzone | `packages/web/src/components/ocr/ocr-dropzone.tsx` | Image dropzone with preview thumbnail |
| OCR result | `packages/web/src/components/ocr/ocr-result.tsx` | Card showing merchant, date, total, line items |
| Save button | Save parsed receipt as transaction |

#### Step 8.2: Flow

1. User drops/selects an image (jpg, png)
2. Image preview shown
3. `POST /api/v1/ocr/process` (multipart)
4. Response: `ParsedReceiptResponse` — show in result card
5. User reviews → clicks "Save as Transaction"
6. Converts receipt data to `TransactionRequest` → `POST /api/v1/transactions/batch`
7. Toast notification on success

---

### Phase 9: Polish & Quality

**Goal:** Error handling, loading states, responsive design, dark mode, accessibility.

#### Step 9.1: Error Handling

| Concern | Implementation |
|---------|---------------|
| API errors | RTK Query `onQueryStarted` error handling → toast notification |
| Network errors | RTK Query retry behavior + offline indicator |
| Form errors | Inline field validation + error summary at top |
| 401 (Unauthorized) | Auto-redirect to login, clear tokens |
| 403 (Forbidden) | Toast with "You don't have permission" |
| 429 (Rate Limited) | Toast with retry-after countdown |
| 500 (Server Error) | Generic error toast with "try again" |

#### Step 9.2: Loading States

| State | Pattern |
|-------|---------|
| Page load | Skeleton components (shadcn `Skeleton`) |
| Table load | `Skeleton` rows |
| Button submit | Loading spinner + disabled state |
| File upload | Progress bar or indeterminate spinner |
| Chart load | `Skeleton` placeholder |

#### Step 9.3: Empty States

| Scenario | Message |
|----------|---------|
| No transactions | "No transactions yet. Upload a CSV or add one manually." + CTA button |
| No search results | "No transactions match your filters." + clear filters button |
| No categories | "No categories found." |
| No uploads yet | "Drop your CSV file here or click to browse." |

#### Step 9.4: Dark Mode

- CSS variable-based theming (shadcn/ui supports this natively)
- Toggle in user menu
- Persist preference in localStorage
- Respect `prefers-color-scheme` on first visit

#### Step 9.5: Responsive Breakpoints

| Breakpoint | Width | Layout Changes |
|------------|-------|----------------|
| `sm` | 640px+ | Single column, stacked cards |
| `md` | 768px+ | Two-column layout where applicable |
| `lg` | 1024px+ | Sidebar visible, full table view |
| `xl` | 1280px+ | Max-width container, optimal spacing |

#### Step 9.6: Accessibility

- All form fields have labels (not just placeholders)
- Error messages linked to inputs via `aria-describedby`
- Focus management on dialog open/close
- Skip-to-content link
- Keyboard navigation for table and dropdowns
- Color contrast meets WCAG AA (4.5:1 for text)

---

### Phase 10: CI/CD Updates

**Goal:** Update GitHub Actions workflows for frontend monorepo.

#### Step 10.1: Update `.github/workflows/pr-build.yml`

Add steps after existing backend job:
```yaml
- name: Setup Node.js
  uses: actions/setup-node@v4
  with:
    node-version: 24

- name: Install pnpm
  uses: pnpm/action-setup@v2
  with:
    version: latest

- name: Install dependencies
  run: pnpm install

- name: Type check
  run: pnpm type-check

- name: Lint
  run: pnpm lint

- name: Build
  run: pnpm build
```

#### Step 10.2: Update `.github/workflows/merge.yml`

Add frontend type-checking step.

#### Step 10.3: Update Docker Compose

Update `docker-compose.yml` to build the web app (Vite build → Nginx serve for production).

---

## File Creation Order (Execution Sequence)

This is the exact sequence to minimize dependency issues:

```
 1. .gitignore (append node_modules/, dist/, .turbo/)
 2. package.json (root)
 3. pnpm-workspace.yaml
 4. turbo.json
 5. tsconfig.json (root, base config)
 6. packages/shared/package.json
 7. packages/shared/tsconfig.json
 8. packages/shared/scripts/generate-types.sh
 9. packages/shared/src/index.ts
10. pnpm install (shared built first)
11. packages/web/package.json
12. packages/web/vite.config.ts
13. packages/web/tsconfig.json
14. packages/web/index.html
15. packages/web/src/index.css
16. packages/web/src/lib/utils.ts
17. packages/web/src/lib/base-query.ts       # Custom RTK Query baseQuery with auth
18. packages/web/src/lib/token-storage.ts     # localStorage-based token persistence
19. packages/web/src/main.tsx
20. packages/web/src/App.tsx
21. pnpm install (full install)
22. npx shadcn@latest init (in packages/web)
23. npx shadcn@latest add button input label card dialog ... (add components)
24. packages/web/src/store/index.ts
25. packages/web/src/store/auth-slice.ts
26. packages/web/src/store/api-slice.ts
27. packages/web/src/store/api/*.ts
28. packages/web/src/router.tsx
29. packages/web/src/components/guards/auth-guard.tsx
30. packages/web/src/layouts/app-layout.tsx
31. packages/web/src/layouts/auth-layout.tsx
32. packages/web/src/pages/login-page.tsx       # Phase 3: Auth
33. packages/web/src/pages/register-page.tsx
34. packages/web/src/components/auth/auth-card.tsx
35. packages/web/src/pages/upload-page.tsx       # Phase 5: CSV Upload
36. packages/web/src/components/csv/*.tsx
37. packages/web/src/pages/transactions-page.tsx # Phase 6: Transaction List
38. packages/web/src/components/transactions/*.tsx
39. packages/web/src/pages/transaction-detail-page.tsx
40. packages/web/src/pages/dashboard-page.tsx    # Phase 7: Dashboard
41. packages/web/src/components/dashboard/*.tsx
42. packages/web/src/pages/upload-page.tsx        # Phase 8: OCR (extend upload page)
43. packages/web/src/components/ocr/*.tsx
44. Backend: TransactionSummaryController.java   # Phase 7 prerequisite
45. CI/CD workflow updates
```

---

## Design Decisions

### Why RTK Query (Over TanStack Query)

Per ADR-011, RTK Query is chosen because:
- Native Redux integration — cache invalidation and optimistic updates work with the Redux devtools
- One fewer dependency — RTK is already pulled in by `@reduxjs/toolkit`
- Tag-based invalidation makes sense for this app's data model (transactions change → invalidate list + dashboard)
- Custom `baseQuery` provides a single place for auth token handling and 401→refresh

### Why recharts Over Chart.js

- React-native (native React components, not canvas)
- TypeScript-first API
- Lighter bundle (~50KB vs ~80KB for react-chartjs-2)
- Simpler API for pie/bar charts

### Why RTK Query Handles Everything (No Separate ApiClient)

RTK Query's `fetchBaseQuery` handles JSON *and* multipart file uploads through a custom `baseQuery`. A single HTTP layer means:
- One place for auth headers (`Authorization`, `X-Client-Id`)
- One 401→refresh flow (custom `baseQuery` intercepts and retries)
- Automatic cache invalidation on mutations (batch save → invalidate `Transaction` tag)
- No duplication between an `ApiClient` class and RTK Query endpoints

Each non-web platform (desktop, mobile) implements its own HTTP client with platform-specific token storage (Electron `safeStorage`, Keychain/Keystore).

### Why Auto-Generate Types from OpenAPI

The backend already has SpringDoc OpenAPI (`@Schema` annotations on every DTO). Using `openapi-typescript`:
- **Single source of truth**: Java DTOs → OpenAPI spec → TypeScript types
- **Compile-time drift detection**: changing a Java DTO field breaks the frontend build immediately
- **Zero manual sync**: no copy-pasting DTOs, no forgotten fields, no stale types
- **Fits the toolchain**: `openapi-typescript` is a Node.js CLI, runs as a pnpm workspace script

### Why a Backend Aggregation Endpoint for Dashboard

Fetching all transactions for client-side aggregation scales poorly (500+ transactions = megabytes of data). A dedicated endpoint:
- Uses efficient database `GROUP BY` queries with existing indexes
- Returns ~3-5KB instead of potentially megabytes
- Supports pagination for historical browsing
- Separates the dashboard data concern from the transaction list concern

### Token Refresh Strategy

```
Request → 401 Response → Custom baseQuery intercepts → POST /api/v1/auth/refresh
  → Success: update stored tokens, retry original request with new token
  → Failure: clear tokens, dispatch logout, redirect to /login
```

The refresh logic lives in the custom RTK Query `baseQuery` wrapper — a single place for the entire web client. Desktop and mobile implement their own refresh logic in their platform-specific HTTP layers.

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Tailwind v4 breaking changes | Low | Medium | Pin exact version, use stable shadcn/ui version |
| shadcn/ui init changes | Medium | Low | Manual component setup if CLI changes |
| Backend API contract drift | Low | High | Auto-generated types from OpenAPI catch drift at build time |
| CORS issues in dev | Low | Medium | Vite proxy handles `/api/*` → `localhost:8080` |
| pnpm workspace resolution | Low | Medium | Verify `@finance-tracker/shared` is resolved correctly |
| Backend aggregation endpoint delay | Medium | Medium | Build dashboard last (Phase 7); can use client-side aggregation as temporary fallback |
| `openapi-typescript` OpenAPI compat | Low | Low | Backend serves OpenAPI 3.0 via SpringDoc; `openapi-typescript` supports 3.0 and 3.1 |

---

## Success Criteria

- [ ] `pnpm install` succeeds from root with all workspace packages
- [ ] `pnpm build` produces production builds for `shared` and `web`
- [ ] `pnpm gen:types` generates TypeScript interfaces from running backend OpenAPI spec
- [ ] Login/Register flow works end-to-end against running backend
- [ ] Transaction list renders paginated data with working filters
- [ ] CSV upload: file → preview → edit → batch save works
- [ ] OCR upload: image → result → save works
- [ ] Dashboard shows summary stats and charts
- [ ] Dark mode toggle works
- [ ] Responsive layout works on 375px, 768px, 1280px widths
- [ ] CI passes with frontend type-checking and linting

---

## Related

- ADR-007: Vite + React + Tailwind + shadcn/ui Frontend
- ADR-011: Frontend Monorepo with Multi-Client Architecture
- Plan README: Phases 9–10 (Shared Package + Web App)
