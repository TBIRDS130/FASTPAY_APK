#!/bin/bash
# Run unit tests (no device). From repo root. Same as test-unit.ps1 (Windows).
# Usage: bash scripts/test-unit.sh [VersionFolder]
#   VersionFolder: e.g. FASTPAY_BASE (default)

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[ ! -d "$ROOT" ] && { echo "Error: repo root not found"; exit 1; }
cd "$ROOT" || exit 1

VERSION="${1:-FASTPAY_BASE}"
VERSION_PATH="$ROOT/$VERSION"
if [ ! -f "$VERSION_PATH/gradlew" ]; then
  echo "Not a Gradle project: $VERSION_PATH"
  exit 1
fi

echo "Running unit tests ($VERSION)..."
cd "$VERSION_PATH" || exit 1
./gradlew testDebugUnitTest --no-daemon
EXIT=$?
cd "$ROOT" || exit 1
exit $EXIT
