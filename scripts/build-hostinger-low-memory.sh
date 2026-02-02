#!/bin/bash
# Build FASTPAY_BASE on Hostinger (low memory / limited threads).
# Run from repo root: bash scripts/build-hostinger-low-memory.sh
# Or from FASTPAY_BASE: bash ../scripts/build-hostinger-low-memory.sh

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/FASTPAY_BASE"

# Reduce memory and threads so we don't hit "unable to create native thread"
export GRADLE_OPTS="-Xmx512m -Xms128m -XX:MaxMetaspaceSize=128m"
export JAVA_TOOL_OPTIONS=""   # clear host's restrictive limit if set

# Single worker, no daemon, low heap for workers too
./gradlew --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx512m -Xms128m -XX:MaxMetaspaceSize=128m" \
  -Dorg.gradle.parallel=false \
  -Dorg.gradle.caching=true \
  assembleRelease

echo "APK: $ROOT/FASTPAY_BASE/app/build/outputs/apk/release/"
