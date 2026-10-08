# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Vite + React 18+ + TypeScript + Tailwind CSS v4 + shadcn/ui. Redux Toolkit + RTK Query for state management. Monorepo via pnpm + Turborepo. Backend: Spring Boot 4.1 + Java 21 + PostgreSQL.

Future: desktop (Electron), mobile (React Native → native).

## Users

Primary user is the developer (solo founder), managing personal finances. If the tool gains traction, it expands to other individuals who want automated transaction tracking without manual entry.

## Product Purpose

A personal finance tracker that eliminates manual data entry. Upload bank CSV statements or photograph receipts — the system parses, categorizes, and deduplicates transactions automatically. See where your money goes without spreadsheets.

## Positioning

Most finance trackers require manual entry or read-only bank feeds. This one starts from what you already have: bank export CSVs and receipt photos. No API keys, no Plaid dependency, no monthly subscription to see your own data. Future: direct wallet and bank connections if the tool proves itself.

## Operating Context

Desktop web browser. User has CSV files from their bank(s) and/or receipt photos from purchases. They want to upload, review parsed data in a preview grid, confirm, and see categorized spending on a dashboard. Sessions are JWT-authenticated. Self-hosted via Docker Compose.

## Capabilities and Constraints

| Capability | Status |
|---|---|
| CSV bank statement upload + parsing | ✅ Built (backend) |
| Auto-categorization by merchant keyword | ✅ Built |
| Deduplication (same date + amount within 2 days) | ✅ Built |
| Receipt OCR (photo → merchant, date, total, line items) | ✅ Built |
| JWT auth + refresh tokens | ✅ Built |
| Multi-client API (`X-Client-Id` header, rate limiting) | ✅ Built |
| OpenAPI/Swagger docs | ✅ Built |
| Observability (OTel + Grafana stack) | ✅ Built |
| Web UI (Login, CSV Upload, Transaction List, Dashboard, OCR) | 🟡 To build |
| Desktop app (Electron) | 📋 Planned |
| Mobile app (React Native → native) | 📋 Planned |
| Bank/wallet API connections | 🔮 Future (if product gains traction) |

Constraints:
- Solo developer — scope must stay focused
- Self-hosted (Docker Compose) — no cloud infra
- OCR uses OpenAI Vision API (per-token cost)
- PostgreSQL for storage — no other database supported

## Brand Commitments

Working name: **Finance Tracker**. No logo, no visual identity established yet. Blank slate for design.

## Evidence on Hand

- Complete backend with integration tests and API contract
- Architecture plan with ADRs (001–013)
- PostgreSQL schema with Flyway migrations
- Sample bank CSV parsing pipeline
- No real user data yet

## Product Principles

1. **Automate everything possible** — CSV parsing, categorization, deduplication, OCR. The user's job is to review and confirm, not to enter data.
2. **Preview before persist** — every import shows a review grid before saving. Mistakes are caught before they enter the database.
3. **Privacy by default** — self-hosted, no third-party data sharing, receipt images not retained after OCR.
4. **Multi-device, one backend** — the same API serves web, desktop, and mobile. Shared types prevent contract drift.
5. **Start small, grow deliberately** — personal tool first. Bank/wallet API connections only if the product proves its value.

## Accessibility & Inclusion

No product-specific accessibility requirements established yet. Standard WCAG AA compliance targeted for web UI.
