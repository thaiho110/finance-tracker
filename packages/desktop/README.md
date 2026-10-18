# Kept — desktop

Electron shell around the web app (ADR-011, phase 11). The window renders `packages/web` unchanged;
the main process serves it from `app://finance`, forwards `app://finance/api/*` to the backend and
stores the session encrypted with the OS keychain (Electron `safeStorage`).

```powershell
# backend running on http://localhost:8080 (docker compose --profile all up -d)
pnpm --filter @finance-tracker/desktop dev        # window with hot reload
pnpm --filter @finance-tracker/desktop package    # unpacked app in packages/desktop/release
pnpm --filter @finance-tracker/desktop dist       # installer (NSIS on Windows)
```

Point it at another backend with `FINANCE_TRACKER_API_URL`, e.g.
`$env:FINANCE_TRACKER_API_URL="http://192.168.1.20:8080"; pnpm --filter @finance-tracker/desktop dev`.

Requests are sent with `X-Client-Id: finance-tracker-desktop` (100 requests/minute).
