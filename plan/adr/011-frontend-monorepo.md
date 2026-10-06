# ADR-011: Frontend Monorepo with Multi-Client Architecture

**Status:** Accepted  
**Date:** 2026-10-07  
**Deciders:** Thai Ho

## Context

The Finance Tracker backend (Spring Boot 4.1, REST API at `/api/v1/`) is fully built. The next major scope is the frontend layer, which must serve three client types:

- **Web** — browser-based (Vite + React per ADR-007)
- **Desktop** — native desktop app (Electron)
- **Mobile** — native mobile app (React Native, with future migration to native Android/Swift)

Each client needs to authenticate via JWT + `X-Client-Id`, call the same REST endpoints, handle file uploads (CSV, receipt images), and share business logic such as validation rules and TypeScript types.

## Decision Drivers

- **Code reuse** — shared types, API client, and validation logic across all three clients
- **Atomic changes** — one commit can touch backend, shared types, and frontend code
- **CI efficiency** — only rebuild packages that changed (Turborepo caching)
- **Familiar stack** — team knows React, TypeScript, and Electron
- **No vendor lock-in** — bare React Native avoids Expo subscription requirement

## Decisions

### 1. Monorepo with pnpm + Turborepo

A single repository houses all frontend packages alongside the existing backend.

```
finance-tracker/
├── backend/                          # Spring Boot 4.1 (existing)
├── packages/
│   ├── shared/                       # Types, API client, validation
│   ├── web/                          # Vite + React + Tailwind + shadcn/ui
│   ├── desktop/                      # Electron + React (electron-vite)
│   └── mobile/                       # Bare React Native
├── .github/workflows/                # CI/CD for both backend and frontend
├── pnpm-workspace.yaml
├── turbo.json
└── package.json
```

**Rationale:**
- **pnpm** over npm: faster installs, disk-efficient hard links, strict dependency isolation
- **Turborepo** over plain workspaces: build caching, parallel task execution, dependency graph awareness
- One PR can make an atomic change across backend + shared types + web UI

### 2. State Management: Redux Toolkit + RTK Query

- **Redux Toolkit (RTK)** for client state (auth status, UI preferences, theme)
- **RTK Query** for server state (transactions, categories, CSV/OCR results)

RTK Query replaces TanStack Query by providing native Redux integration for API caching, refetching, optimistic updates, and cache invalidation — all without extra dependencies.

### 3. Shared Package (`@finance-tracker/shared`)

A TypeScript library consumed by all three clients:

| Module | Contents |
|--------|----------|
| `types/` | `Transaction`, `UserProfile`, `AuthResponse`, `Page<T>`, `OcrResult`, `ApiError` |
| `api/` | `ApiClient` class with auth token injection, error handling, refresh token logic |
| `api/token-storage.ts` | `TokenStorage` interface — each platform provides its own implementation |
| `validation/` | `validateEmail`, `validatePassword`, `validateAmount`, form validators |

The `ApiClient` accepts a `TokenStorage` interface. Each platform injects:
- **Web**: `localStorage`-backed storage
- **Desktop**: Electron IPC → secure file storage
- **Mobile**: `react-native-keychain` (Keychain/Keystore)

### 4. Web Client: Vite + React + TypeScript + shadcn/ui

Per ADR-007, with these specifics:

| Concern | Choice |
|---------|--------|
| Build tool | Vite |
| UI framework | React 18+ with TypeScript |
| Styling | Tailwind CSS v4 |
| Component library | shadcn/ui (Radix primitives) |
| Icons | lucide-react |
| Table | @tanstack/react-table |
| File upload | react-dropzone |
| Dev proxy | Vite proxy: `/api/*` → `http://localhost:8080` |

**Build order for pages:**
1. Login — form + JWT storage → redirect to dashboard
2. Transaction list — paginated table with filters
3. CSV upload — drag-and-drop, preview grid, confirm save
4. Dashboard — category breakdown, totals, charts (Chart.js or recharts)

### 5. Desktop Client: Electron + electron-vite

- **electron-vite** — shares Vite config with web app, provides HMR for renderer
- Reuses web React components for UI (transaction list, CSV upload, dashboard)
- Platform-specific code:
  - Electron main process: window management, file dialogs (CSV/receipt picker)
  - Preload script: exposes secure IPC bridge for token storage and file system access
  - `TokenStorage` implementation via Electron's `safeStorage` API

### 6. Mobile Client: Bare React Native

- **Bare React Native** (not Expo) — avoids Expo subscription requirement for production builds
- **React Navigation** — bottom tab navigator (Transactions, Upload, Settings) + stack navigator for detail screens
- Platform-specific code:
  - `react-native-document-picker` for CSV file selection
  - `react-native-vision-camera` for receipt photo capture
  - `react-native-keychain` for secure JWT storage
  - `TokenStorage` implementation wrapping Keychain/Keystore

Future migration path: individual screens → native Android (Kotlin) / iOS (Swift) as team grows.

### 7. Build Order

| Phase | Package | Duration | Deliverable |
|-------|---------|----------|-------------|
| 1 | `packages/shared/` | Day 1–2 | Types, API client, validation |
| 2 | `packages/web/` | Week 1–3 | Login → Transactions → CSV → Dashboard |
| 3 | `packages/desktop/` | Week 4–5 | Electron shell wrapping web components |
| 4 | `packages/mobile/` | Week 6–8 | React Native with shared API client |

### 8. CI/CD Integration

Update existing GitHub Actions workflows:

- **PR Build** (`pr-build.yml`): add `pnpm install && pnpm build` and `pnpm lint` steps
- **Merge to Main** (`merge.yml`): add frontend type-checking on push

Turborepo caching ensures unchanged packages are skipped on CI.

## Consequences

### Positive
- Shared types prevent API contract drift between clients
- Atomic commits across backend + frontend simplify code reviews
- Turborepo caching keeps CI fast as the codebase grows
- React knowledge transfers across web, desktop, and mobile
- No vendor lock-in on mobile (bare RN → native migration path)

### Negative
- pnpm + Turborepo adds tooling complexity vs. a single npm package
- Bare React Native requires Android SDK / Xcode setup for mobile development
- Electron results in larger desktop bundles vs. Tauri (~150MB vs ~10MB)
- Redux + RTK Query adds boilerplate vs. Zustand + TanStack Query

### Mitigations
- Tooling complexity is a one-time setup cost, amortized over the project lifetime
- Android SDK / Xcode setup is documented in the project README
- Desktop bundle size is acceptable for internal tool use
- RTK Query significantly reduces boilerplate vs. classic Redux

## Related

- ADR-007: Vite + React + Tailwind + shadcn/ui Frontend
- ADR-009: Multi-Client API Design
- ADR-006: Spring Boot 3.x Backend (now 4.1)
