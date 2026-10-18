# Kept — mobile

Bare React Native app (ADR-011, phase 12) on the shared API client (`@finance-tracker/shared`).

| Tab | What it does | Needs |
|---|---|---|
| Transactions | List (pull to refresh, infinite scroll), open one to correct or delete it | `TRANSACTION_READ` (`TRANSACTION_WRITE` to edit) |
| Scan | Take or pick a receipt photo, review what was read, save with its line items | `RECEIPT_SCAN` |
| Import | Pick a CSV statement, choose rows (duplicates unticked), save | `CSV_IMPORT` |
| Settings | Account, server, sign out | — |

Tabs follow the account's permissions (ADR-018). Tokens live in the Android Keystore / iOS Keychain
(`react-native-keychain`); requests carry `X-Client-Id: finance-tracker-mobile`.

## Run it

Prerequisites: Node 22+, pnpm, and for Android: Android Studio (SDK + an emulator) and JDK 17+.
iOS needs a Mac with Xcode.

```powershell
pnpm install                                   # from the repository root
docker compose --profile all up -d             # backend on http://localhost:8080
pnpm --filter @finance-tracker/mobile start    # Metro bundler
pnpm --filter @finance-tracker/mobile android  # second terminal: build + install on the emulator/phone
```

On the sign-in screen, set **Server**:

- Android emulator: `http://10.0.2.2:8080` (the default)
- iOS simulator: `http://localhost:8080`
- Real phone: your computer's LAN address, e.g. `http://192.168.1.20:8080` (same Wi-Fi; allow port 8080 in the Windows firewall)

Debug builds allow plain `http://`. Release builds need `https://` (or set `usesCleartextTraffic`).

## Monorepo notes

- The workspace uses `nodeLinker: hoisted`, so packages are in the repository root `node_modules`;
  `android/settings.gradle` and `android/app/build.gradle` point there.
- `metro.config.js` watches the whole workspace; `@finance-tracker/shared` is bundled from its
  TypeScript source through the `react-native` export condition (no build step needed).

## Checks

```powershell
pnpm --filter @finance-tracker/mobile type-check
pnpm --filter @finance-tracker/mobile test
```
