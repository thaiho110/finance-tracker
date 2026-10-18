# Finance Tracker — Brand

Logo, color, type and accessibility rules for every client (web, Electron, React Native, devices).
Extends [`../README.md`](../README.md) and [`../tokens.css`](../tokens.css); nothing here replaces the existing UI tokens except the contrast fixes in §3.

## 1. Logo

**Concept: receipt → chart.** A torn-edge receipt carrying a rising bar chart, on a brand-green tile.
The receipt is what the user brings (receipt photos, bank statements). The bars are what they get back (where the money went).
It states the product promise from `PRODUCT.md` — *no manual entry, start from what you already have* — in one shape, and it replaces the generic lucide `Wallet` placeholder that hundreds of finance apps share.

| File | Use |
|---|---|
| `mark.svg` | Primary app mark. Sidebar, auth card, about screen, README. 24 px and up. |
| `favicon.svg` / `favicon.ico` | Browser tab. Simplified (fewer, bolder teeth and bars) so it holds at 16 px. |
| `icon-fullbleed.svg` | Square full-bleed source for iOS and Android adaptive/maskable icons (OS applies the mask). |
| `apple-touch-icon.png` | 180 px iOS home-screen icon. |
| `icon-192.png`, `icon-512.png` | PWA manifest `any` icons. |
| `icon-maskable-512.png` | PWA manifest `maskable` icon (art inside the 80 % safe zone). |
| `icon-1024.png` | Electron / app-store master. Generate `.icns`/`.ico` from it with `electron-builder`. |
| `glyph-mono.svg` | One-color glyph, `fill="currentColor"`. Monochrome contexts: e-ink/LCD dedicated devices, Android notification icon, watermark, print. |
| `lockup.svg` / `lockup-dark.svg` | Horizontal mark + wordmark for light / dark backgrounds. |
| `lockup-stacked.svg` / `lockup-stacked-dark.svg` | Centered lockup for login, splash and empty states. |

**Rules**
- Clear space around the mark: ¼ of the tile width on every side. Nothing else inside it.
- Minimum size: mark 24 px (below that use `favicon.svg`); horizontal lockup 120 px wide.
- The tile is always `#1E6B47` with a white receipt, in both light and dark UI. Only the wordmark color changes (`#17191A` on light, `#E8E8E6` on dark).
- Don't recolor the tile per category, add gradients/shadows, rotate it, stretch it, or put the full-color mark on a busy photo.
- Wordmark is outlined Geist SemiBold, −2 % tracking. It's paths, not live text, so it renders the same with or without the font loaded.
- Alt text: `alt="Finance Tracker"` when the mark stands alone; `alt=""` when it sits next to the visible product name.

**Wiring it into `packages/web`**
```html
<!-- index.html -->
<link rel="icon" href="/favicon.ico" sizes="48x48">
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="/apple-touch-icon.png">
<meta name="theme-color" content="#1E6B47">
```
Copy the icons into `packages/web/public/`, and replace the `<Wallet>` tiles in `layouts/app-layout.tsx` and `layouts/auth-layout.tsx` with `<img src="/mark.svg" alt="" className="size-8" />` (the tile is part of the SVG, so drop the green wrapper div).

## 2. Color

Keep the existing palette. Brand green `#1E6B47` already reads as money, growth and "confirmed", it hits 6.46 : 1 against white, and the warm neutral canvas keeps the dashboard calm.

| Role | Light | Dark |
|---|---|---|
| Brand / logo tile | `#1E6B47` | `#1E6B47` (logo) · `#5DBB8A` (UI accent) |
| Text | `#17191A` | `#E8E8E6` |
| Canvas | `#F6F5F1` | `#0F1113` |

## 3. Contrast audit (WCAG 2.2 AA) and fixes

Measured against the tokens in `packages/web/src/index.css`. Text needs 4.5 : 1 (1.4.3); UI component boundaries and chart marks need 3 : 1 (1.4.11).

| Token | Was | Ratio (on) | Fix | New ratio |
|---|---|---|---|---|
| `text-muted` (light) | `#6A6E68` | 4.47 on `bg-muted` — table headers fail | `#5F635D` | 5.27 |
| `status-warning` (light) | `#A86A12` | 4.43 white / 3.92 warning-soft | `#8F5A0B` | 5.78 / 5.11 |
| `chart-3` amber (light) | `#E0A13A` | 2.25 on white — slice/dot fails 3 : 1 | `#B7791F` | 3.64 |
| `border-default` used on inputs | `#E3E1D9` | 1.31 — input outline invisible to low vision | new `border-input` `#8A877D` | 3.59 white / 3.29 canvas |
| `brand-primary` (dark) | `#3D8B63` | white text on it 4.14; as link text 4.13 | `#5DBB8A` + `text-on-primary` `#0F1113` | 8.06 button / 7.28 link |
| `chart-1` (dark) | `#3D8B63` | 4.13 (ok for 3 : 1, but match the new accent) | `#5DBB8A` | 7.28 |
| `status-danger` (dark) | `#D9534F` | 4.31 on surface | `#E5675F` (dark text on filled buttons) | 5.24 |
| `border-input` (dark) | — | — | `#737670` | 3.71 |

Everything else passes: text 17.6, muted-on-white 5.19, primary-on-soft 5.50, danger 5.67, dark muted 5.63.

```css
/* Drop-in patch for packages/web/src/index.css */
@theme {
  --color-text-muted: #5F635D;
  --color-status-warning: #8F5A0B;
  --color-chart-3: #B7791F;
  --color-border-input: #8A877D;   /* inputs, selects, checkboxes, dropzone */
}
.dark {
  --color-brand-primary: #5DBB8A;
  --color-text-on-primary: #0F1113;
  --color-chart-1: #5DBB8A;
  --color-status-danger: #E5675F;
  --color-border-input: #737670;
}
```
`border-default` stays for cards and row dividers — those are decorative and exempt.

## 4. Typography

**Geist** for UI, **Geist Mono** for every amount and date — already installed, keep it. It's neutral, has full Vietnamese diacritics, and the mono keeps digits aligned in tables.

**Gap found:** neither Geist nor Geist Mono has the **₫** glyph (U+20AB), so every amount falls back to a random system font for the currency sign. Fix by adding JetBrains Mono's Vietnamese subset as the next font in the stack (verified to contain ₫); the browser only uses it for glyphs Geist Mono lacks:

```bash
pnpm --filter web add @fontsource/jetbrains-mono
```
```ts
// main.tsx
import '@fontsource/jetbrains-mono/vietnamese-500.css';
import '@fontsource/jetbrains-mono/vietnamese-600.css';
```
```css
--font-mono: 'Geist Mono Variable', 'JetBrains Mono', ui-monospace, monospace;
```

Type scale stays as in `../README.md` (Display 28 · Heading 20 · Title 16 · Body 14 · Caption 12 · Mono 14/28). Keep body text ≥ 14 px and captions ≥ 12 px.

## 5. WCAG 2.2 AA checklist for the clients

| SC | What it means here |
|---|---|
| 1.1.1 Non-text content | Logo alt per §1. Charts get a visually hidden data table or `aria-label` summarizing totals. |
| 1.4.1 Use of color | Category badges always show the label; duplicate rows show the alert icon *and* the tint; income vs expense uses a sign (+/−), not only color. |
| 1.4.3 / 1.4.11 Contrast | §3 tokens. Recheck any new token with the same ratios. |
| 1.4.4 / 1.4.10 Resize & reflow | Works at 200 % zoom and 320 px width — the review grid scrolls horizontally inside its own container. |
| 2.1.1 Keyboard | Dropzone opens with Enter/Space; editable grid cells reachable by Tab; dialogs trap focus and close on Esc. |
| 2.4.7 / 2.4.11 Focus visible, not obscured | 2 px `brand-primary` ring with 2 px offset; the sticky save bar must not cover the focused row (add `scroll-padding-bottom`). |
| 2.5.8 Target size | Icon buttons (pencil, trash, pagination) at least 24 × 24 px; 44 × 44 on mobile. |
| 3.3.1 / 3.3.3 Errors | Field errors in text below the field, linked with `aria-describedby`. |
| 4.1.3 Status messages | "12 transactions saved", parsing progress and OCR results announced via `role="status"`. |
| 2.3.3 Motion | Honor `prefers-reduced-motion` for chart and dialog animation. |
