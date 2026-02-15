# VPS Setup Guide

Complete guide for setting up FASTPAY Android development environment on a fresh Linux VPS.

## Quick Start

For a completely fresh VPS, run this single command:

```bash
curl -sSL https://raw.githubusercontent.com/your-repo/FASTPAY_APK/main/scripts/setup-vps.sh | bash
```

Or clone and run:

```bash
git clone https://github.com/your-repo/FASTPAY_APK.git
cd FASTPAY_APK
bash scripts/setup-vps.sh
```

## Minimum VPS Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| **RAM** | 2GB | 4GB+ |
| **CPU** | 1 vCPU | 2+ vCPU |
| **Storage** | 20GB | 50GB+ SSD |
| **OS** | Ubuntu 20.04+, Debian 10+, CentOS 8+, Amazon Linux 2 | Ubuntu 22.04 LTS |

## Supported Cloud Providers

### AWS EC2
- **Instance Types**: t3.medium (2GB RAM), t3.large (8GB RAM)
- **AMI**: Ubuntu 22.04 LTS or Amazon Linux 2
- **Security Group**: Allow SSH (port 22), optional HTTP/HTTPS for web services

### DigitalOcean
- **Droplets**: Basic (2GB RAM), Standard (4GB RAM)
- **Image**: Ubuntu 22.04 LTS
- **Firewall**: SSH access required

### Linode
- **Plans**: Linode 2GB, Linode 4GB
- **Distribution**: Ubuntu 22.04 LTS
- **Network**: SSH access configured

### Vultr
- **Plans**: Regular Performance (2GB RAM)
- **OS**: Ubuntu 22.04 LTS
- **Firewall**: SSH access enabled

## Manual Setup Steps

### 1. Connect to VPS

```bash
ssh root@your-vps-ip
```

### 2. Update System (Ubuntu/Debian)

```bash
apt update && apt upgrade -y
```

### 3. Create Non-Root User (Recommended)

```bash
adduser fastpay
usermod -aG sudo fastpay
su - fastpay
```

### 4. Run Setup Script

```bash
# If cloned repository
bash scripts/setup-vps.sh

# Or direct download
curl -sSL https://raw.githubusercontent.com/your-repo/FASTPAY_APK/main/scripts/setup-vps.sh | bash
```

## Distribution-Specific Instructions

### Ubuntu/Debian
```bash
# Install dependencies manually (if not using script)
sudo apt update
sudo apt install -y curl unzip git wget ca-certificates \
    openjdk-17-jdk build-essential lib32stdc++6 lib32z1
```

### CentOS/RHEL 8+
```bash
# Install dependencies manually
sudo dnf update -y
sudo dnf groupinstall -y "Development Tools"
sudo dnf install -y curl unzip git wget ca-certificates java-17-openjdk-devel
```

### Amazon Linux 2
```bash
# Install dependencies manually
sudo yum update -y
sudo yum groupinstall -y "Development Tools"
sudo yum install -y curl unzip git wget ca-certificates java-17-openjdk-devel
```

## What Gets Installed

### System Components
- **Java 17** (OpenJDK)
- **Build Tools**: curl, unzip, git, wget
- **Development Libraries**: lib32stdc++6, lib32z1
- **Python 3** (for some Android tools)

### Android Development
- **Android SDK** (installed to `~/android-sdk`)
- **Android Platform Tools** (ADB, fastboot)
- **Android API 36** (compile/target SDK)
- **Build Tools 36.0.0**
- **Command-line Tools**

### Environment Configuration
- **ANDROID_HOME** set to `~/android-sdk`
- **PATH** updated with Android tools
- **JAVA_HOME** configured
- **Bash profile** updated permanently

## Headless Development

The setup works completely without GUI:

### Building APKs
```bash
cd FASTPAY_BASE
./gradlew assembleDebug
./gradlew assembleRelease
```

### Using Build Scripts
```bash
# Debug build
bash scripts/test-build.sh

# Release build
bash scripts/release-build.sh
```

### Testing Without Physical Device
```bash
# Create AVD (Android Virtual Device) - optional
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n test -k "system-images;android-36;google_apis;x86_64"

# Launch emulator (requires display) - not needed for builds
$ANDROID_HOME/emulator/emulator -avd test
```

## Security Considerations

### SSH Security
```bash
# Disable password authentication
sudo nano /etc/ssh/sshd_config
# Set: PasswordAuthentication no
sudo systemctl restart ssh
```

### Firewall Setup
```bash
# Ubuntu/Debian
sudo ufw allow ssh
sudo ufw enable

# CentOS/RHEL
sudo firewall-cmd --permanent --add-service=ssh
sudo firewall-cmd --reload
```

### User Permissions
```bash
# Keep Android SDK in user directory (not system-wide)
# Avoid using root for development
# Regular user has all necessary permissions
```

## Troubleshooting

### Common Issues

#### "Java not found"
```bash
# Check Java installation
java -version
# Reinstall if needed
sudo apt install openjdk-17-jdk  # Ubuntu/Debian
sudo dnf install java-17-openjdk-devel  # CentOS/RHEL
```

#### "Android SDK not found"
```bash
# Check environment variables
echo $ANDROID_HOME
# Source bash profile
source ~/.bashrc
```

#### "Permission denied"
```bash
# Fix Gradle wrapper permissions
chmod +x FASTPAY_BASE/gradlew
```

#### "Out of memory"
```bash
# Check available memory
free -h
# Consider upgrading VPS plan
# Or use Gradle options: ./gradlew assembleDebug -Dorg.gradle.jvmargs=-Xmx2g
```

### Build Issues

#### "SDK license not accepted"
```bash
# Accept all licenses
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
```

#### "Missing platform"
```bash
# Install required platform
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-36"
```

#### "Build tools missing"
```bash
# Install build tools
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "build-tools;36.0.0"
```

## Performance Optimization

### Gradle Performance
```bash
# Configure Gradle for low-memory systems
echo "org.gradle.jvmargs=-Xmx1g -XX:MaxPermSize=256m" >> FASTPAY_BASE/gradle.properties
echo "org.gradle.parallel=true" >> FASTPAY_BASE/gradle.properties
echo "org.gradle.daemon=true" >> FASTPAY_BASE/gradle.properties
```

### Build Caching
```bash
# Enable build cache
echo "android.enableBuildCache=true" >> FASTPAY_BASE/gradle.properties
```

## Validation

After setup, validate the installation:

```bash
# Run health check
bash scripts/vps-health-check.sh

# Test build
cd FASTPAY_BASE
./gradlew assembleDebug

# Check Android tools
adb version
```

## Next Steps

1. **Clone Repository** (if not already done)
2. **Configure Environment** (copy `.env.example` to `.env`)
3. **Setup Keystore** (for release builds)
4. **Run First Build** (`bash scripts/release-build.sh`)
5. **Setup CI/CD** (optional - use existing GitHub Actions)

## Support

For issues with VPS setup:

1. Check the troubleshooting section above
2. Run the health check script
3. Verify system requirements are met
4. Check cloud provider documentation for specific issues

## Automation

The setup script can be automated in user data scripts:

### AWS EC2 User Data
```bash
#!/bin/bash
curl -sSL https://raw.githubusercontent.com/your-repo/FASTPAY_APK/main/scripts/setup-vps.sh | bash
```

### DigitalOcean User Data
```bash
#!/bin/bash
curl -sSL https://raw.githubusercontent.com/your-repo/FASTPAY_APK/main/scripts/setup-vps.sh | bash
```

This enables completely automated VPS provisioning for FASTPAY development.
