# UI Rework — Status Report

## ✅ Completed

| File | Description |
|------|-------------|
| `package.json` | Added `@fontsource-variable/geist-mono` |
| `src/index.css` | Full design token system (colors, radius, fonts), dark mode, reusable utilities (`surface-card`, `input-base`, `focus-ring`, `error-ring`, `th-base`, `td-base`, `field-label`, `page-title`, `nav-item`) |
| `src/lib/utils.ts` | Currency (VND ₫), date formatting, category tone mapping, chart colors |
| `src/components/ui/button.tsx` | Primary/Secondary/Ghost/Destructive variants, design spec sizing |
| `src/components/ui/badge.tsx` | Neutral/outline/default variants |
| `src/components/ui/input.tsx` | 36px tall, 8px radius, uses reusable utilities |
| `src/components/ui/card.tsx` | 12px radius, 20px padding |
| `src/components/ui/table.tsx` | 12px radius wrapper, reusable cell utilities |
| `src/components/ui/dialog.tsx` | 480px, 16px radius, 45% scrim |
| `src/components/ui/category-badge.tsx` | **New** — Tone colors with 6px dot indicator |
| `src/components/ui/stat-card.tsx` | **New** — Mono/28 values, delta, icon |
| `src/layouts/auth-layout.tsx` | 400px card, brand-primary colors |
| `src/layouts/app-layout.tsx` | 240px sidebar, dynamic page title in top bar |
| `src/pages/login-page.tsx` | Error banner, field-level error states |
| `src/pages/register-page.tsx` | Password strength meter, validation |
| `src/pages/dashboard-page.tsx` | Month switcher, stat cards with deltas, donut chart (center total + legend), bar chart (trend chip), recent transactions |
| `src/pages/transactions-page.tsx` | Filters bar, sortable table, inline edit dialog, delete confirmation, pagination with rows-per-page |

## ✅ Completed in follow-up (2026-10-18)

| File | Description |
|------|-------------|
| `src/pages/upload-page.tsx` | Segmented CSV/Receipt tabs; dashed dropzone with "Browse files" + "How it works" strip; file card with parsed/duplicate chips and "Choose another file"; editable review grid (inline cells, category select with badges, duplicate rows, row filter, remove); sticky save bar with total + validation; saved state ("Import another" / "View transactions"); "Quick scan again" for the last imported file; parse/OCR error panels with retry; two-column receipt layout (image preview + reading overlay, privacy note, editable fields with "Auto-detected"/"Edited" marks, duplicate banner, line items with "add up to total" check) |
| `src/pages/transaction-detail-page.tsx` | Design tokens, ₫ formatting, CategoryBadge, categories from API, validation, receipt items table with total check |
| `src/App.tsx` | Light mode by default; dark only when `ft_dark === 'true'` |
| `src/index.css` | **Fixes:** `@theme inline` → `@theme` so the `.dark` token set actually applies (utilities now use `var(--color-…)`); removed self-referencing `--color-chart-N: var(--color-chart-N)` lines; `surface-card` used an invalid `ring:` property (no border) → `border: 1px solid` |
| `src/components/ui/pagination.tsx` | `outline` button variant no longer exists → `secondary` |
| `src/pages/dashboard-page.tsx` | Implicit `any` type errors fixed |

**Verified:** `tsc --noEmit` passes and `vite build` succeeds.

## Follow-ups (not part of the rework)

- Receipt line items are shown but not saved — `TransactionRequest` has no `lineItems` field (see ADR-014 §6).
- `src/lib/utils.ts` `cn()` is now a plain join (no `tailwind-merge`), so conflicting classes passed via `className` don't override component defaults. Most components import `cn` from the `cn` package instead; worth making them consistent.
- JS bundle is ~1 MB — consider lazy-loading pages / recharts.

## Design Token Reference

All reusable CSS utilities are in `src/index.css`:
- `surface-card` — white bg, 12px radius, border ring
- `input-base` — 36px, 8px radius, border
- `focus-ring` / `error-ring` — focus/error states
- `th-base` / `td-base` — table cells
- `field-label` — form field labels (caption style)
- `page-title` — page heading (20px semibold)
- `nav-item` — sidebar navigation item
