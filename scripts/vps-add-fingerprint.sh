#!/bin/bash
# Run this script ON the VPS to add the allowed SHA256 fingerprint on the machine.
# Usage (on VPS, from repo root): bash scripts/vps-add-fingerprint.sh
# For system-wide file /etc/fastpay/: sudo bash scripts/vps-add-fingerprint.sh

set -e

FINGERPRINT="SHA256:u0EflYlbTILs0FBvagUbpWGVnJ9m5BgHBxxq5VeR0Ak"

# Use /etc/fastpay if run as root, else user config
if [ -w /etc ] 2>/dev/null; then
  DIR="/etc/fastpay"
  FILE="${DIR}/allowed-fingerprints.txt"
else
  DIR="${HOME}/.config/fastpay"
  FILE="${DIR}/allowed-fingerprints.txt"
fi

mkdir -p "$DIR"

if [ -f "$FILE" ] && grep -qFx "$FINGERPRINT" "$FILE" 2>/dev/null; then
  echo "Fingerprint already present in $FILE"
  exit 0
fi

# Add header on first write
if [ ! -f "$FILE" ]; then
  echo "# Allowed SHA256 certificate fingerprints (VPS)" > "$FILE"
  echo "# One per line. Format: SHA256:base64" >> "$FILE"
fi
echo "$FINGERPRINT" >> "$FILE"
echo "Added fingerprint to $FILE"
