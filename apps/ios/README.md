# Kept for iPhone (native SwiftUI)

A native iOS client for the Kept backend. It uses the same REST API as the web, desktop and React Native
clients (`/api/v1`, JWT with refresh, `X-Client-Id: finance-tracker-mobile`).

## What's in it

- Sign in against a server address you type on the login screen (saved on the device).
- **Overview**: month switcher, spending vs last month, a donut chart by category (Swift Charts), budget progress bars, top merchants.
- **Transactions**: grouped by day with daily totals, search, category icons, swipe or long-press to delete, add with a big amount field and category grid.
- **Settings**: Face ID lock, change server, sign out.
- **Shortcuts / Siri**: an "Add expense to Kept" action (amount, merchant, optional category) for quick capture.
- Tokens live in the iOS Keychain.

Not here yet: CSV import, receipt scan, budgets editing, categories and rules, admin. Those still live in the web app.

## Run it (one command)

```bash
cd apps/ios && ./run-simulator.sh
```

It installs XcodeGen if needed, generates the project, builds, and launches the app in an iPhone simulator.

## Run it from Xcode

Needs a Mac with Xcode 15+ (iOS 17 SDK).

```bash
brew install xcodegen
cd apps/ios
xcodegen generate          # creates Kept.xcodeproj from project.yml
open Kept.xcodeproj
```

Pick an iPhone simulator and press Run. Tests: `Cmd+U`, or

```bash
xcodebuild test -project Kept.xcodeproj -scheme Kept \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

### Pointing it at your backend

- Simulator: `http://localhost:8080` works when the backend runs on the same Mac.
- Real iPhone: use the Mac's LAN address, e.g. `http://192.168.1.20:8080`. The app allows plain HTTP to
  local-network hosts only (`NSAllowsLocalNetworking`); anything on the internet must be HTTPS.
- Running on a real device needs a signing team: set it under Signing & Capabilities after generating the project.
  A free Apple ID works, but the install expires after 7 days.

## Notes

- The Xcode project is generated, not committed, so merges stay clean. Edit `project.yml`, not the project.
- Amounts are treated as whole ₫ (VND has no minor unit), so "45,000", "45.000" and "45000" all mean 45000.
- The Shortcuts intent files a transaction under the category you name, else "Other", else your first expense category.
