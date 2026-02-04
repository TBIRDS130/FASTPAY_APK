#!/bin/bash
# Download the release APK from your VPS to your local machine.
# Run this from your LOCAL machine (not on the VPS).
#
# Usage:
#   bash scripts/download-apk-from-vps.sh user@vps-hostname-or-ip
#   bash scripts/download-apk-from-vps.sh root@123.45.67.89
#
# Optional: specify version folder and destination
#   VERSION=FASTPAY_BASE DEST=./ bash scripts/download-apk-from-vps.sh user@vps

set -e

VPS="${1:-}"
if [ -z "$VPS" ]; then
  echo "Usage: bash scripts/download-apk-from-vps.sh user@vps-hostname-or-ip"
  echo ""
  echo "Example: bash scripts/download-apk-from-vps.sh root@123.45.67.89"
  exit 1
fi

VERSION="${VERSION:-FASTPAY_BASE}"
REMOTE_PATH="/opt/FASTPAY_APK/$VERSION/app/build/outputs/apk/release/fastpay-3.0-release.apk"
DEST="${DEST:-.}"

echo "Downloading APK from $VPS:$REMOTE_PATH -> $DEST/"
scp "$VPS:$REMOTE_PATH" "$DEST/"
echo "Done. Install with: adb install -r $DEST/fastpay-3.0-release.apk"
