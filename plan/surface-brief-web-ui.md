# Surface Brief: Finance Tracker Web UI

## 1. Job and Audience

**Visitor mode:** Operate — the user completes tasks, reviews data, and leaves.

**User:** Solo founder managing personal finances. Arrives in a desktop browser, weekly or daily, with bank CSV exports or receipt photos. Also does one-time data dumps (import years of history). Already authenticated (JWT session persists).

**Need:** See where money went without manual data entry. Upload, review, categorize, and understand spending in the fewest clicks.

## 2. Outcome and Proof

**Primary task:** Import financial data (CSV or receipt) → review parsed results → confirm save → see the impact on spending overview.

**Success:** User drops a CSV, sees clean parsed transactions in a preview grid, clicks save, and immediately sees the new data reflected in the dashboard and list. The whole cycle takes under 30 seconds.

**Product-specific truth:** No manual entry. No Plaid/bank API dependency. Starts from what the user already has — bank export files and receipt photos.

## 3. Surfaces and Interaction

### Surface A: App Shell (Layout)

Shared by all authenticated pages.

| Element | Behavior |
|---------|----------|
| **Sidebar** | Collapsible left nav. Icons + labels: Dashboard, Transactions, Import (CSV + OCR). Active page highlighted. Collapsed state shows icons only (tooltip on hover). |
| **Top bar** | Page title (dynamic, matches active route). Right side: user email + avatar dropdown (Logout, Dark Mode toggle). |
| **Main content** | Fills remaining width. Responsive: sidebar collapses to overlay on <1024px, bottom tab bar on <640px. |

### Surface B: Login / Register (Public)

Two centered cards (Login / Register) with toggle between them. No sidebar, no top bar.

| State | Design |
|-------|--------|
| **Default** | Email + password fields, "Sign In" button, link to register. Clean, minimal, no decoration. |
| **Loading** | Button shows spinner, fields disabled. |
| **Error** | Inline error above form (not toast) — "Invalid email or password" or "Email already registered". Field-level validation on blur. |
| **Already authenticated** | Redirect to `/dashboard` immediately (no flash of login page). |

### Surface C: CSV Upload

The primary import surface. User stays here after saving to do another import.

| Element | Behavior |
|---------|----------|
| **Dropzone** | Large dashed-border area in center-top. "Drop CSV here or click to browse". Accepts `.csv` only. Shows filename + file size after selection. |
| **Quick Scan** | Small button next to or below dropzone: "Quick Scan" — re-uploads the most recent file (cached) without re-selecting. Appears only after at least one successful import this session. |
| **Preview Grid** | Editable table of `ParsedTransactionResponse[]`. Columns: Date (datepicker), Description (text), Merchant (text), Amount (number), Category (dropdown). Rows are editable inline. Duplicate rows have a warning icon + yellow tint. "Remove row" (trash icon) per row. |
| **Save Bar** | Sticky bottom bar: "Save X transactions" primary button. Shows count. Changes to "Saving..." with spinner during save. After save: green checkmark + "X transactions saved" + option to "Import another" (clears grid) or "View transactions" (navigates to list). |
| **Empty** | Dropzone centered. Helper text: "Supported: CSV files from your bank. Maximum 10MB." |
| **Parsing error** | Inline error replacing preview grid: "Could not parse this file. [detail]" + "Try another file" button. |

### Surface D: Transaction List

Paginated, filterable table of saved transactions.

| Element | Behavior |
|---------|----------|
| **Filters bar** | Category dropdown (populated from `GET /api/v1/categories`), date range picker (from/to), search input (filters `rawDescription` + `cleanMerchant`). "Clear filters" link when any filter is active. |
| **Table** | @tanstack/react-table. Columns: Date (sortable), Merchant (sortable), Description (truncated, tooltip on hover), Amount (sortable, currency format), Category (badge), Source (badge: CSV/OCR/Manual), Duplicate (icon), Actions (edit/delete buttons). |
| **Pagination** | Page controls bottom-right. Configurable page size (10/25/50). Shows "X of Y transactions". |
| **Empty (no data)** | "No transactions yet. [Upload a CSV] to get started." CTA button links to `/upload`. |
| **Empty (no results)** | "No transactions match your filters." + "Clear filters" button. |
| **Edit** | Dialog/modal pre-filled with transaction data. Save updates via `PUT /api/v1/transactions/{id}` and refreshes list. |
| **Delete** | Confirmation dialog: "Delete this transaction?" with transaction details. Confirm → `DELETE` → remove row + toast. |

### Surface E: Dashboard

Overview with summary stats, charts, and recent activity. First thing the authenticated user sees.

| Element | Behavior |
|---------|----------|
| **Summary cards row** | 3-4 cards: Total Expenses (this month), Total Income (if any), Transaction Count (this month), Average Transaction. Values from `GET /api/v1/transactions/summary`. |
| **Category PieChart** | recharts PieChart. Each slice = category, sized by total amount. Legend to right or below. Click a slice → filter to that category (navigate to transaction list with filter). |
| **Monthly BarChart** | recharts BarChart. Last 6 months, each bar = total spending that month. Hover shows exact amount. |
| **Recent transactions** | Last 5 transactions list (compact: date, merchant, amount, category badge). Click → navigate to transaction detail. |
| **Loading** | Skeleton placeholders for cards + charts. |
| **Empty (no data)** | "Welcome! Upload your first CSV to see your spending breakdown." + "Upload CSV" CTA button. No charts shown (no data to chart). |

### Surface F: OCR Receipt

Tab on the Import page alongside CSV Upload.

| Element | Behavior |
|---------|----------|
| **Tab bar** | Two tabs: "CSV Upload" | "Receipt Scan". Active tab highlighted. |
| **Image dropzone** | Same style as CSV dropzone. Accepts `.jpg`, `.png`. Shows image thumbnail preview after selection. |
| **OCR result card** | After parsing: card showing Merchant name, Date, Total amount, Category (auto-detected, editable dropdown), and line items table (item + price). Duplicate warning if applicable. |
| **Save button** | "Save as Transaction" → converts to `TransactionRequest` → `POST /api/v1/transactions/batch`. After save: toast + option to scan another. |
| **Error** | "Could not read this receipt. Try a clearer photo." + "Try another" button. |

## 4. Scope and Boundaries

**Fidelity:** Production-ready. Every state (loading, empty, error, success, edge case) handled.

**Breadth:** All 5 surfaces built end-to-end. No placeholder screens.

**Interactivity:** Full CRUD on transactions. File upload with drag-and-drop. Inline editing in preview grid. Sortable/filterable/paginated table.

**Out of scope (explicit anti-goals):**
- No bank/wallet API connections
- No AI analysis (future, noted in PRODUCT.md)
- No native mobile app (planned separately)
- No PWA/offline mode
- No multi-user/team features
- No budgeting or goal tracking
- No receipt image storage (retained only during OCR processing)

## 5. States and Ranges

| Surface | Typical Data | Max Data | Edge Cases |
|---------|-------------|----------|------------|
| CSV Upload | 50-200 rows per file | 10,000 rows | Empty CSV, wrong format, duplicate file, network failure mid-upload |
| Transaction List | 500-2000 transactions | 50,000+ | Pagination at max, slow queries with complex filters |
| Dashboard | 100-500 txns this month | 5000+ txns | First visit (0 txns), month with no spending, year-end with max data |
| OCR Receipt | 1 receipt | 1 receipt (single upload) | Blurry photo, non-receipt image, partial scan, network failure |

## 6. Interaction and Layout

**Layout hierarchy (desktop):**
```
┌──────────────┬──────────────────────────────────────┐
│              │  Top Bar (page title, user menu)      │
│   Sidebar    ├──────────────────────────────────────┤
│   (240px)    │                                       │
│              │         Main Content Area             │
│  Dashboard   │                                       │
│  Transactions│     (fills remaining width)           │
│  Import      │                                       │
│              │                                       │
└──────────────┴──────────────────────────────────────┘
```

**Responsive behavior:**
- ≥1024px: Sidebar visible, fixed 240px
- 640-1023px: Sidebar collapses to hamburger menu (overlay)
- <640px: Bottom tab bar replaces sidebar (4 tabs: Dashboard, Transactions, Import, Settings/Profile)

**Feedback patterns:**
- All mutations: optimistic update → toast on success → revert + error toast on failure
- File uploads: indeterminate progress during parse, result replaces dropzone
- Navigation: no page transitions (instant), SPA-style
- Dark mode: CSS variable swap, no page reload

## 7. Constraints and Open Decisions

**Confirmed constraints:**
- RTK Query for all API calls (custom `baseQuery` for auth)
- Types auto-generated from OpenAPI spec (`openapi-typescript`)
- Tailwind CSS v4 + shadcn/ui components
- lucide-react for icons
- recharts for charts
- `@tanstack/react-table` for tables
- react-dropzone for file uploads
- Vite proxy: `/api/*` → `http://localhost:8080`

**Open decisions (for build phase):**
- Color palette and typography (ui-ux-pro-max design system generation)
- Exact sidebar icon set
- Toast positioning (bottom-right vs top-right)
- Date format locale (user-configurable or fixed YYYY-MM-DD)
- Whether to show income/expense split or just expenses (backend has no income concept yet)

**Delivery:** Self-hosted Docker Compose. Production build via Vite → Nginx.
