#!/bin/bash
# Install the release APK from VPS to your Android device via wireless ADB.
# Run this ON the VPS (after building the APK).
#
# Prerequisites:
#   1. Android device with Developer Options > Wireless debugging enabled
#   2. Note the IP:PORT from your phone's Wireless debugging screen
#   3. VPS has platform-tools (adb) - from install-android-sdk-vps.sh
#
# Usage:
#   bash scripts/vps-install-via-adb.sh 192.168.1.100:5555
#   DEVICE_IP=192.168.1.100 DEVICE_PORT=5555 bash scripts/vps-install-via-adb.sh

set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${2:-FASTPAY_BASE}"
APK="$ROOT/$VERSION/app/build/outputs/apk/release/fastpay-3.0-release.apk"

# Accept IP:PORT as first arg, or separate env vars
DEVICE=""
if [ -n "$1" ] && [[ "$1" == *:* ]]; then
  DEVICE="$1"
elif [ -n "${DEVICE_IP:-}" ] && [ -n "${DEVICE_PORT:-}" ]; then
  DEVICE="$DEVICE_IP:$DEVICE_PORT"
fi

if [ -z "$DEVICE" ]; then
  echo "Usage: bash scripts/vps-install-via-adb.sh <device-ip>:<port>"
  echo ""
  echo "On your Android phone: Settings > Developer options > Wireless debugging"
  echo "  - Turn it ON, then tap it to see IP address and port (e.g. 192.168.1.100:5555)"
  echo ""
  echo "Example: bash scripts/vps-install-via-adb.sh 192.168.1.100:5555"
  exit 1
fi

if [ ! -f "$APK" ]; then
  echo "APK not found: $APK"
  echo "Build first: bash scripts/build-with-sdk.sh $VERSION"
  exit 1
fi

# Use adb from ANDROID_HOME if set
ADB="adb"
if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
  ADB="$ANDROID_HOME/platform-tools/adb"
fi

echo "Connecting to device $DEVICE..."
"$ADB" connect "$DEVICE"

echo "Installing APK..."
"$ADB" -s "$DEVICE" install -r "$APK"

echo "Done. Open FastPay on your device."
