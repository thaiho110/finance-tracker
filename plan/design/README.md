# Finance Tracker — Web UI Design

Design exported from Figma for the web client (`packages/web`). It implements the surfaces in [`../surface-brief-web-ui.md`](../surface-brief-web-ui.md) and follows the stack in ADR-007 / ADR-013 (React + Tailwind v4 + shadcn/ui, lucide-react, recharts, @tanstack/react-table, react-dropzone).

- **Figma source:** https://www.figma.com/design/lwfvt0XzhLJEfSfyYVq2DG — page **Web UI**. The Components board sits on the left and the screens are to its right.
- **Frame size:** 1440 px desktop (sidebar 240 px + main area). Images are exported at 2x.
- **Mode:** light only. The Figma Starter plan allows one variable mode, so dark mode isn't drawn. Build it as a second token set in CSS.

```
plan/design/
├── README.md          ← this file
├── tokens.css         ← color, radius and font tokens as CSS variables
├── screens/           ← 7 full-page PNGs (2x)
├── components/        ← one PNG per component + 00-overview.png
└── icons/             ← 20 SVG icons (stroke="currentColor", lucide geometry)
```

---

## 1. Foundations

### Colors (`tokens.css`)

| Token | Hex | Use |
|---|---|---|
| `bg/canvas` | `#F6F5F1` | App background, top bar |
| `bg/surface` | `#FFFFFF` | Cards, tables, inputs, dialogs |
| `bg/muted` | `#EFEEE8` | Table header rows, tabs track, merchant tiles |
| `bg/sidebar` | `#FBFAF7` | Sidebar |
| `border/default` | `#E3E1D9` | Card, input and row dividers |
| `border/strong` | `#CFCCC1` | Dashed dropzone, active pagination |
| `text/default` | `#17191A` | Primary text |
| `text/muted` | `#6A6E68` | Secondary text, column headers, raw bank descriptions |
| `text/on-primary` | `#FFFFFF` | Text on primary / destructive buttons |
| `brand/primary` | `#1E6B47` | Primary buttons, active nav, focus ring, links |
| `brand/primary-soft` | `#E2F0E8` | Active nav background, success chips |
| `status/warning` (+`-soft`) | `#A86A12` / `#FBF0D9` | Duplicate rows, duplicate banners |
| `status/danger` (+`-soft`) | `#B93A2C` / `#FAE5E2` | Errors, destructive actions |
| `chart/1…6` | see `tokens.css` | One color per category (charts and badges) |

**Category → color mapping** (keep the same mapping in charts, legends and badges):

| Category | Token | Badge tone |
|---|---|---|
| Food & Dining | `chart/1` | Green |
| Transport | `chart/2` | Teal |
| Shopping | `chart/3` | Amber |
| Bills & Utilities | `chart/4` | Coral |
| Other / Uncategorized | `chart/5` | Slate (or Neutral) |
| Entertainment | `chart/6` | Purple |

### Typography

| Style | Font | Size / line height | Use |
|---|---|---|---|
| Display/28 | Geist SemiBold | 28 / 36 | Login heading, text-only stat value |
| Heading/20 | Geist SemiBold | 20 / 28 | Page title (top bar), dialog title |
| Title/16 | Geist SemiBold | 16 / 24 | Card titles |
| Body/14 | Geist Regular | 14 / 20 | Body text, descriptions |
| Label/14 | Geist Medium | 14 / 20 | Buttons, field labels, merchant names |
| Caption/12 | Geist Medium | 12 / 16 | Helper text, column headers (uppercase, +4% tracking) |
| Mono/14 | Geist Mono Medium | 14 / 20 | **All dates and amounts** in tables |
| Mono/28 | Geist Mono SemiBold | 28 / 36 | Stat card values |

Geist is already loaded via `@fontsource-variable/geist`. Add `@fontsource-variable/geist-mono` for amounts and dates. A monospace font keeps digits aligned in columns.

### Spacing and radius

- Page padding is 32 px left/right and 24–28 px top. Gaps between page sections are 16–24 px.
- Cards use 20 px padding. Table rows are 48–52 px tall with 16 px left padding.
- Radius: 6 px for small controls, 8 px for buttons and inputs, 12 px for cards and tables, 16 px for dialogs and the dropzone. Badges and chips are pills (999).

### Formatting rules

- **Currency:** VND with the `₫` prefix and comma thousands separators (`₫1,240,000`). Use short form only in tight spots (`₫12.48M` in the donut center and chart tooltip). Use `Intl.NumberFormat('en-US')` and prefix `₫`.
- **Dates:** `Oct 16` in lists and the dashboard. Use ISO `2026-10-16` in the import preview and edit forms so they match the CSV and the date picker.
- **Amount column:** right-aligned, monospace.

---

## 2. Components

All components are in Figma on the **Components** board. The PNGs are in `components/`. The "Code" column shows the matching file in `packages/web/src/components/ui` or the component to create.

| Component | Image | Figma properties | Code |
|---|---|---|---|
| **Button** | `button.png` | `Variant` = Primary · Secondary · Ghost · Destructive; `Label` (text); `Show icon` (bool); `Icon` (swap) | `ui/button.tsx` → `default` · `outline` · `ghost` · `destructive` |
| **Badge** | `badge.png` | `Tone` = Neutral · Green · Teal · Amber · Coral · Slate · Purple; `Label` | `ui/badge.tsx` + a `CategoryBadge` wrapper |
| **Nav item** | `nav-item.png` | `State` = Default · Active; `Label`; `Icon` (swap) | Sidebar link (`NavLink` + `cn`) |
| **Input** | `input.png` | `Text`; `Show icon` (bool); `Icon` (swap: search, calendar) | `ui/input.tsx` (+ leading-icon wrapper) |
| **Stat card** | `stat-card.png` | `Label`, `Value`, `Delta` | `ui/card.tsx` → `StatCard` |
| **Sidebar** | `sidebar.png` | — (set the active `Nav item` per route) | `layouts/app-layout.tsx` |
| **Top bar** | `top-bar.png` | `Title` | `layouts/app-layout.tsx` (+ `ui/dropdown-menu.tsx`, `ui/avatar.tsx`) |
| **Recent row** | `recent-row.png` | `Merchant`, `Initial`, `Date`, `Amount` + nested Badge | Dashboard "Recent transactions" list |
| **Preview row** | `preview-row.png` | `Date`, `Description`, `Merchant`, `Amount`, `Duplicate warning` (bool) + nested Badge | CSV preview grid row (editable) |
| **Transaction row** | `transaction-row.png` | `Date`, `Merchant`, `Description`, `Amount`, `Duplicate` (bool) + category and source Badges | `ui/table.tsx` row with @tanstack/react-table |
| **Line item row** | `line-item-row.png` | `Item`, `Price` | Receipt OCR line-items table |

### Usage notes

**Button**
- Use **one Primary per view**: the main forward action (Import CSV, Save N transactions, Save as transaction, Sign in).
- Use **Secondary** for alternatives (Choose another file, Browse files, Scan another, Cancel), **Ghost** for low-emphasis actions (View all, Discard, Clear filters), and **Destructive** only inside the delete confirmation dialog.
- Icons are 16 px and sit before the label. Hide the icon on text-only actions (Cancel, Save changes, Sign in).
- Sizing: 14 px horizontal / 8 px vertical padding, 8 px gap, 36 px tall.

**Badge**
- **Category badges** take the tone of their category (see the mapping above). **Source badges** (CSV / OCR / Manual) always use **Neutral**.
- The background is the tone color at 14% opacity, with a solid 6 px dot and default text color. In CSS that's `bg-[color]/15` plus a dot `span`.

**Nav item**
- Active state: `brand/primary-soft` background, with icon and label in `brand/primary`. Default state: muted icon and default text.
- Icons are 18 px. Item height is 36 px, full sidebar width minus 16 px padding on each side.

**Input**
- 36 px tall, 8 px radius, 1 px `border/default`. Use the `brand/primary` border for focus and `status/danger` for errors. Error text goes below the field in Caption/12 danger.
- The leading icon is optional: search for filters, calendar for date pickers.
- Amounts and dates in inputs use Mono/14.

**Stat card**
- Value uses Mono/28. If the value is text (e.g. "Food & Dining"), switch to Display/28.
- Delta is a muted caption, for example "+8.5% vs September".

**Preview row (CSV import grid)**
- Every cell is editable inline: Date → date picker, Description/Merchant → text, Category → select showing a Badge, Amount → number.
- **Duplicate:** set `Duplicate warning` = true. This shows the alert icon before the date and fills the row with `status/warning-soft`.
- **Editing cell:** 1.5 px `brand/primary` border and a light `brand/primary-soft` row tint. Empty merchant shows the placeholder "Add merchant".
- The trash button removes the row from the batch before saving.

**Transaction row**
- Columns: Date 104 · Merchant 170 · Description fill (truncate with ellipsis, full text in tooltip) · Amount 120 (right) · Category 168 · Source 84 · Dup 24 · Actions 72.
- Sortable headers (Date, Merchant, Amount) show a chevron. The active sort column header uses default text color.
- Hover state: row background `bg/canvas`. Pencil opens the Edit dialog (`PUT`) and trash opens a delete confirm (`DELETE`).

**Icons** (`icons/*.svg`) use `currentColor`, so they inherit the text color. In code, use the matching `lucide-react` component rather than the SVG files:

| File | lucide-react |
|---|---|
| dashboard | `LayoutDashboard` |
| transactions | `ArrowLeftRight` |
| upload | `Upload` |
| search | `Search` |
| chevron-down / -left / -right | `ChevronDown` / `ChevronLeft` / `ChevronRight` |
| trash | `Trash2` |
| pencil | `Pencil` |
| alert | `TriangleAlert` |
| check | `Check` |
| calendar | `Calendar` |
| file-csv | `FileSpreadsheet` |
| camera | `Camera` |
| panel-left | `PanelLeft` |
| wallet (logo) | `Wallet` |
| trending-up | `TrendingUp` |
| moon | `Moon` |
| x | `X` |
| log-out | `LogOut` |

---

## 3. Pages

### 01 · Login — error state (`screens/01-login-error.png`)
Route `/login`. Public layout with no sidebar.
- Centered 400 px card: logo, "Welcome back" heading, then Email and Password fields and a full-width Primary "Sign in" button. A link below the card switches to Register.
- **Error:** an inline banner above the fields (`status/danger-soft`), not a toast. The failing field gets a danger border.
- **Loading:** spinner in the button and fields disabled. **Already signed in:** redirect to `/dashboard` before rendering.

### 02 · Dashboard (`screens/02-dashboard.png`)
Route `/dashboard`. The first page after login. Data comes from `GET /api/v1/transactions/summary`.
- **Header:** month switcher (‹ October 2026 ›) on the left, Primary "Import CSV" on the right.
- **Summary cards (4):** Total expenses · Transactions · Average transaction · Top category, each with a delta against the previous month. There's no income card because the backend has no income concept yet.
- **Spending by category:** donut chart (recharts `PieChart`, inner radius about 66%) with the month total in the center and a legend showing amount and %. **Clicking a slice** navigates to `/transactions?category=…`.
- **Monthly spending:** last 6 months as bars (recharts `BarChart`). The current month uses `brand/primary` and past months use `brand/primary-soft`. Hover shows a dark tooltip. The trend chip shows the month-on-month change.
- **Recent transactions:** the last 5 as *Recent row*, with a "View all" link to `/transactions`.
- **Loading:** skeletons for cards and charts. **Empty:** a welcome message plus an "Upload CSV" button, with charts hidden.

### 03 · Import — CSV, empty (`screens/03-import-csv-empty.png`)
Route `/upload`, **CSV Upload** tab.
- A large dashed dropzone (react-dropzone, `.csv` only, 10 MB max) with a "Browse files" button.
- A 3-step "how it works" strip below helps first-time users.

### 04 · Import — CSV, preview (`screens/04-import-csv-preview.png`)
The same route after a file is parsed.
- The dropzone collapses into a **file card**: name, size and parse time, a "128 parsed" chip, a "2 possible duplicates" chip, and "Choose another file".
- **Review grid:** *Preview row* × N, with a filter input in the grid toolbar. Duplicate rows are highlighted. One row shows the inline-edit state.
- **Sticky save bar:** count and total on the left, with the duplicate warning beneath. "Discard" (Ghost) and "Save 128 transactions" (Primary) on the right. While saving, the label changes to "Saving…" with a spinner. After saving, show a success state with "Import another" / "View transactions". Saving invalidates the `Transaction` cache tag so the dashboard and list refresh.
- **Parse error:** replace the grid with "Could not parse this file. [detail]" and a "Try another file" button.

### 05 · Import — Receipt scan, result (`screens/05-import-receipt-result.png`)
Route `/upload`, **Receipt Scan** tab, after the OCR finishes.
- **Left column:** image preview (jpg/png), the file row with "Replace", and a privacy note: *"The image is discarded after reading — only the details are saved."*
- **Right column:** "Receipt details" card.
  - A "Possible duplicate" banner (warning-soft) with "View".
  - Merchant, Date, Total and Category fields, each marked "Auto-detected" and editable.
  - The line items table (*Line item row* × N), ending in an "Items add up to total ✓" check row.
- **Save bar:** "Scan another" (Secondary) and "Save as transaction" (Primary). Saving converts the result to a `TransactionRequest` and sends `POST /api/v1/transactions/batch`.
- **Not drawn yet:** the loading state while OCR runs (use an indeterminate progress bar over the preview) and the read error ("Could not read this receipt. Try a clearer photo." plus "Try another").

### 06 · Transactions (`screens/06-transactions.png`)
Route `/transactions`.
- **Filters bar:** search input (matches `rawDescription` + `cleanMerchant`), category select, date range picker (an active filter gets a primary border), and "Clear filters" whenever any filter is active. Primary "Import" sits on the right.
- **Table:** *Transaction row* × page size. One row shows the hover state.
- **Pagination footer:** "Showing 1–10 of 1,284 transactions", rows-per-page select (10/25/50), and page buttons.
- **Empty (no data):** "No transactions yet" plus an "Upload a CSV" button. **Empty (filtered):** "No transactions match your filters" plus "Clear filters".

### 07 · Transactions — Edit dialog (`screens/07-transactions-edit-dialog.png`)
Opened from the pencil action. Uses `ui/dialog.tsx`.
- 480 px dialog with a 45% dark scrim. Title and source line ("Imported from CSV on …"), with a close button (X).
- Fields: Date and Amount side by side, then Merchant, then Description (with the hint *used for auto-categorization*), then the Category select.
- Footer: "Cancel" (Secondary) and "Save changes" (Primary). Saving sends `PUT /api/v1/transactions/{id}`. Update the row optimistically and show a toast, or roll back with an error toast.
- The delete confirm reuses this dialog shell. Show the transaction details, then "Cancel" + "Delete" (**Destructive**).

---

## 4. Responsive (not drawn — from the brief)

| Width | Navigation |
|---|---|
| ≥ 1024 px | Sidebar fixed at 240 px (as drawn) |
| 640–1023 px | Sidebar hidden, hamburger in the top bar opens it as an overlay |
| < 640 px | Bottom tab bar: Dashboard · Transactions · Import · Profile |

Below 1024 px: summary cards wrap 2 × 2 and the dashboard charts stack. In the receipt screen the two columns stack (image first).

## 5. Open items
- Dark mode token set (CSS only, no Figma mode on the Starter plan).
- Receipt scan loading and error states, delete confirm dialog, and toast placement (suggested: bottom-right).
- Tablet and mobile layouts.
