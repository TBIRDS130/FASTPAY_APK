#!/bin/bash
# Build a FastPay version on Hostinger (low memory / limited threads).
# Run from repo root: bash scripts/build-hostinger-low-memory.sh [FASTPAY_BASE]
# Or from FASTPAY_BASE: bash ../scripts/build-hostinger-low-memory.sh

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-FASTPAY_BASE}"
cd "$ROOT/$VERSION"

if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
  echo "Set ANDROID_HOME (or ANDROID_SDK_ROOT) to your Android SDK path."
  exit 1
fi
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"

# Low memory: heap 512m, metaspace 256m (lint needs metaspace; 128m can cause OOM)
export GRADLE_OPTS="-Xmx512m -Xms128m -XX:MaxMetaspaceSize=256m"
export JAVA_TOOL_OPTIONS=""   # clear host's restrictive limit if set

echo "Building $VERSION (assembleRelease) with low-memory settings..."
# Single worker, no daemon, same limits for forked JVMs
./gradlew --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx512m -Xms128m -XX:MaxMetaspaceSize=256m" \
  -Dorg.gradle.parallel=false \
  -Dorg.gradle.caching=true \
  assembleRelease

echo "APK: $ROOT/$VERSION/app/build/outputs/apk/release/"
