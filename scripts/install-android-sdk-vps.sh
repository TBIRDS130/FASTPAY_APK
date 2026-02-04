#!/bin/bash
# Install Android command-line SDK on this VPS so you can build FASTPAY_BASE.
# Run once (with or without sudo). Then: export ANDROID_HOME=<install-dir> and run build-with-sdk.sh
#
# Usage:
#   sudo bash scripts/install-android-sdk-vps.sh              # install to /opt/android-sdk
#   bash scripts/install-android-sdk-vps.sh                    # install to $HOME/android-sdk
#   ANDROID_SDK_INSTALL=/path/to/sdk bash scripts/install-android-sdk-vps.sh

set -e

INSTALL_DIR="${ANDROID_SDK_INSTALL:-}"
if [ -z "$INSTALL_DIR" ]; then
  if [ -w /opt ]; then
    INSTALL_DIR="/opt/android-sdk"
  else
    INSTALL_DIR="${HOME}/android-sdk"
  fi
fi

echo "Installing Android SDK to: $INSTALL_DIR"
mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"

# Command-line tools (latest) - use dl.google.com (commandlinetools.android.com often fails to resolve on VPS)
CMDLINE_VERSION="13114758"
if [ ! -d "cmdline-tools/latest" ]; then
  echo "Downloading command-line tools..."
  curl -sL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip" -o /tmp/cmdline-tools.zip
  unzip -q -o /tmp/cmdline-tools.zip -d /tmp
  mkdir -p cmdline-tools
  mv /tmp/cmdline-tools cmdline-tools/latest
  rm -f /tmp/cmdline-tools.zip
fi

export ANDROID_HOME="$INSTALL_DIR"
export PATH="$INSTALL_DIR/cmdline-tools/latest/bin:$INSTALL_DIR/platform-tools:$PATH"

# Accept licenses and install components required by FASTPAY_BASE (compileSdk 36, buildTools 36.0.0)
echo "Installing platform-tools, platform android-36, build-tools 36.0.0..."
yes | sdkmanager --licenses 2>/dev/null || true
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

echo ""
echo "Done. Add to your shell (e.g. ~/.bashrc) and run the build:"
echo "  export ANDROID_HOME=$INSTALL_DIR"
echo "  export PATH=\"\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH\""
echo ""
echo "Then from FASTPAY_APK root:"
echo "  bash scripts/build-with-sdk.sh"
echo ""
