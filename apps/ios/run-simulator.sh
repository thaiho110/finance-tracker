#!/usr/bin/env bash
# Builds Kept and launches it in the iPhone Simulator. Needs a Mac with Xcode installed.
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v xcodegen >/dev/null; then
  command -v brew >/dev/null || { echo "Install Homebrew first: https://brew.sh"; exit 1; }
  brew install xcodegen
fi

xcodegen generate

# First available iPhone simulator (e.g. "iPhone 17 Pro").
DEVICE=$(xcrun simctl list devices available | grep -m1 "iPhone" | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/')
[ -n "$DEVICE" ] || { echo "No iPhone simulator found. In Xcode: Settings > Components > install an iOS simulator."; exit 1; }
echo "Using simulator $DEVICE"

xcrun simctl boot "$DEVICE" 2>/dev/null || true
open -a Simulator

xcodebuild -project Kept.xcodeproj -scheme Kept -destination "id=$DEVICE" \
  -derivedDataPath build CODE_SIGNING_ALLOWED=NO build

APP=$(find build/Build/Products -maxdepth 3 -name Kept.app | head -1)
xcrun simctl install "$DEVICE" "$APP"
xcrun simctl launch "$DEVICE" com.financetracker.kept
echo "Kept is running in the Simulator. Sign in with your server address (http://localhost:8080 if the backend runs on this Mac)."
