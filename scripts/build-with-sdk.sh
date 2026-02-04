#!/bin/bash
# Build FastPay using ANDROID_HOME (Option A).
# Set your SDK path once, then run:
#   export ANDROID_HOME=$HOME/Android/Sdk   # or your SDK path
#   bash scripts/build-with-sdk.sh [FASTPAY_BASE]
# Or one-liner: ANDROID_HOME=/path/to/sdk bash scripts/build-with-sdk.sh

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-FASTPAY_BASE}"
cd "$ROOT/$VERSION"

if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
  echo "Set ANDROID_HOME (or ANDROID_SDK_ROOT) to your Android SDK path."
  echo "Example: export ANDROID_HOME=\$HOME/Android/Sdk"
  exit 1
fi
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
if [ ! -d "$ANDROID_HOME" ]; then
  echo "Android SDK not found at: $ANDROID_HOME"
  exit 1
fi

echo "Building $VERSION (assembleRelease) with ANDROID_HOME=$ANDROID_HOME"
./gradlew assembleRelease
echo "APK: $ROOT/$VERSION/app/build/outputs/apk/release/"
