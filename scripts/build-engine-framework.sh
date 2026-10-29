#!/usr/bin/env bash
# Build the shared :engine KMP module into RunWearEngine.xcframework and
# place it where the Xcode project expects it (RunWear-iOS/Frameworks/,
# gitignored — the framework is a build artifact, never committed).
#
# Used by the iOS CI workflows; can also be run by hand on a Mac.
# Requires: JDK 17 + the Android SDK (for the module's androidTarget) and
# Xcode (Kotlin/Native links against the Apple SDKs).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/RunWear-android"

./gradlew :engine:assembleRunWearEngineXCFramework --stacktrace

SRC="$(find engine/build/XCFrameworks -name 'RunWearEngine.xcframework' -type d | head -1)"
if [ -z "$SRC" ]; then
  echo "ERROR: RunWearEngine.xcframework not found under engine/build/XCFrameworks" >&2
  exit 1
fi

DEST="$ROOT/RunWear-iOS/Frameworks"
mkdir -p "$DEST"
rm -rf "$DEST/RunWearEngine.xcframework"
cp -R "$SRC" "$DEST/RunWearEngine.xcframework"
echo "Placed $DEST/RunWearEngine.xcframework (from $SRC)"
