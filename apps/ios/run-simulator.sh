#!/usr/bin/env bash
# Builds Kept and launches it in the iPhone Simulator. Needs a Mac with Xcode installed.
set -euo pipefail
cd "$(dirname "$0")"

# simctl and xcodebuild come with the full Xcode app, not the Command Line Tools. If xcode-select
# points at the latter, use Xcode for this run only (no sudo needed).
if ! xcrun --find simctl >/dev/null 2>&1; then
  for xcode in /Applications/Xcode.app /Applications/Xcode-beta.app; do
    if [ -d "$xcode/Contents/Developer" ]; then
      export DEVELOPER_DIR="$xcode/Contents/Developer"
      break
    fi
  done
fi
xcrun --find simctl >/dev/null 2>&1 || { echo "Xcode not found in /Applications. Install it from the App Store."; exit 1; }

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
# Simulator.app moves between Xcode versions, so look for it instead of hard-coding the path.
XCODE_APP=$(cd "$(xcode-select -p)/../.." && pwd)
SIM_APP=$(find "$XCODE_APP" -maxdepth 5 -name Simulator.app 2>/dev/null | head -1)
[ -n "$SIM_APP" ] || SIM_APP=$(mdfind "kMDItemCFBundleIdentifier == 'com.apple.iphonesimulator'" 2>/dev/null | head -1)
if [ -n "$SIM_APP" ]; then
  open "$SIM_APP"
else
  echo "Couldn't find Simulator.app. Open it from Xcode: Xcode menu > Open Developer Tool > Simulator."
fi

xcodebuild -project Kept.xcodeproj -scheme Kept -destination "id=$DEVICE" \
  -derivedDataPath build CODE_SIGNING_ALLOWED=NO build

APP=$(find build/Build/Products -maxdepth 3 -name Kept.app | head -1)
xcrun simctl install "$DEVICE" "$APP"
xcrun simctl launch "$DEVICE" com.financetracker.kept
echo "Kept is running in the Simulator. Sign in with your server address (http://localhost:8080 if the backend runs on this Mac)."
