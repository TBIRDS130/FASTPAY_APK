# Base for Cursor cloud/background agents - do NOT copy code; agent clones repo.
# Includes Android SDK, JDK, and common dev tools.

FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# Install base dev tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    git \
    unzip \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install OpenJDK 17 (typical for Android)
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-17-jdk \
    && rm -rf /var/lib/apt/lists/*

# Android SDK (command-line tools only; agent can accept licenses and install components)
ENV ANDROID_HOME=/opt/android-sdk
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -sL "https://commandlinetools.android.com/download?platform=linux" -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp \
    && mv /tmp/cmdline-tools/latest ${ANDROID_HOME}/cmdline-tools/ \
    && rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

# Install API 35 platform and build-tools; accept licenses
RUN yes | sdkmanager --licenses 2>/dev/null || true \
    && sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# Non-root user and workdir (recommended for cloud agents)
RUN useradd -m -s /bin/bash ubuntu
USER ubuntu
WORKDIR /home/ubuntu
