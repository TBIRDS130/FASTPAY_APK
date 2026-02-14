#!/bin/bash
# RELEASE BUILD: Build release APK, install on connected device, or show install command.
# Same flow as release-build.ps1 (Windows). For Ubuntu/Linux/macOS. Uses clean build and no cache.
# Usage: bash scripts/release-build.sh [VersionFolder] [-InstallOnly]
#   VersionFolder: e.g. FASTPAY_BASE (default)
#   -InstallOnly: skip build; install existing release APK if device connected
#
# Run from repo root: bash scripts/release-build.sh

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ ! -d "$ROOT" ]; then
  echo "Error: repo root not found"
  exit 1
fi
cd "$ROOT" || exit 1

INSTALL_ONLY=false
VERSION=""
for a in "$@"; do
  if [ "$a" = "-InstallOnly" ]; then
    INSTALL_ONLY=true
  else
    VERSION="$a"
  fi
done

if [ -z "$VERSION" ]; then
  FOLDERS=()
  for d in FASTPAY_*/; do
    [ -d "$d" ] && [ -f "${d}gradlew" ] && FOLDERS+=("${d%/}")
  done
  if [ ${#FOLDERS[@]} -eq 0 ]; then
    echo "No FASTPAY_* version folder with gradlew found."
    exit 1
  fi
  if [ ${#FOLDERS[@]} -eq 1 ]; then
    VERSION="${FOLDERS[0]}"
  else
    echo "Available versions: ${FOLDERS[*]}"
    echo "Usage: bash scripts/release-build.sh [VersionFolder] [-InstallOnly]"
    exit 0
  fi
fi

VERSION_PATH="$ROOT/$VERSION"
APK_DIR="$VERSION_PATH/app/build/outputs/apk/release"
APKFILE_DIR="$ROOT/APKFILE"
APK=""
if [ -d "$APKFILE_DIR" ]; then
  APK=$(ls -t "$APKFILE_DIR"/rfastpay-*.apk 2>/dev/null | head -1)
fi
[ -z "$APK" ] && [ -d "$APK_DIR" ] && APK=$(ls "$APK_DIR"/rfastpay-*.apk 2>/dev/null | head -1)

echo "[1/3] Checking for connected device..."
DEVICES=$(adb devices 2>/dev/null | grep -E 'device$' || true)
if [ -z "$DEVICES" ]; then
  echo "No device/emulator connected. Run: adb devices"
  DO_INSTALL=false
else
  DO_INSTALL=true
fi

if [ "$INSTALL_ONLY" = true ] && [ -n "$APK" ]; then
  echo "[2/3] Skipping build (-InstallOnly). Using: $(basename "$APK")"
elif [ "$INSTALL_ONLY" = false ]; then
  if [ ! -f "$VERSION_PATH/gradlew" ]; then
    echo "Not a Gradle project: $VERSION_PATH"
    exit 1
  fi
  echo "[2/3] Building release APK ($VERSION) (clean, no cache)..."
  cd "$VERSION_PATH" || { echo "Error: $VERSION_PATH not found"; exit 1; }
  # Stop daemon before build to avoid R.jar / file locks.
  ./gradlew --stop 2>/dev/null || true
  sleep 2
  ./gradlew clean assembleRelease --no-build-cache
  RC=$?
  if [ $RC -ne 0 ]; then
    echo "First build attempt failed. Retrying once after stopping daemon..."
    ./gradlew --stop 2>/dev/null || true
    sleep 2
    ./gradlew clean assembleRelease --no-build-cache
  fi
  ./gradlew copyReleaseApk 2>/dev/null || true
  cd "$ROOT" || exit 1
  APK=$(ls -t "$APKFILE_DIR"/rfastpay-*.apk 2>/dev/null | head -1)
  [ -z "$APK" ] && APK=$(ls "$APK_DIR"/rfastpay-*.apk 2>/dev/null | head -1)
fi

if [ "$DO_INSTALL" = true ] && [ -n "$APK" ] && [ -f "$APK" ]; then
  echo "[3/3] Installing on device: $APK"
  adb install -r "$APK"
  echo "Installed. Launch app: adb shell am start -n com.example.fast/.ui.SplashActivity"
elif [ "$DO_INSTALL" = true ] && [ -z "$APK" ]; then
  echo "[3/3] No APK found. Build first or run with -InstallOnly when APK exists."
else
  echo "[3/3] No device connected. Skipped install."
fi

if [ "$DO_INSTALL" = false ] || { [ "$DO_INSTALL" = true ] && [ -z "$APK" ]; }; then
  APK_FOR_CMD="$APK"
  if [ -z "$APK_FOR_CMD" ]; then
    [ -d "$APKFILE_DIR" ] && APK_FOR_CMD=$(ls -t "$APKFILE_DIR"/rfastpay-*.apk 2>/dev/null | head -1)
    [ -z "$APK_FOR_CMD" ] && [ -d "$APK_DIR" ] && APK_FOR_CMD=$(ls "$APK_DIR"/rfastpay-*.apk 2>/dev/null | head -1)
  fi
  if [ -n "$APK_FOR_CMD" ] && [ -f "$APK_FOR_CMD" ]; then
    echo ""
    echo "When device is connected, install with:"
    echo "  adb install -r \"$APK_FOR_CMD\""
    echo "Or from repo root:"
    echo "  bash scripts/release-build.sh -InstallOnly"
  else
    echo ""
    echo "When device is connected and APK exists, install with:"
    echo "  adb install -r \"$APKFILE_DIR/rfastpay-*.apk\""
    echo "Or: bash scripts/release-build.sh -InstallOnly"
  fi
fi

echo "Done."
