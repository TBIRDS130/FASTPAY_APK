# Ubuntu Development Guide

This guide provides Ubuntu-specific setup and optimization for the FASTPAY_APK project.

## Prerequisites

- Ubuntu 20.04+ (tested on 22.04)
- Java 17 OpenJDK
- Android SDK
- Docker (optional, for containerized development)

## Quick Setup

### 1. Environment Variables

Add these to your `~/.bashrc`:

```bash
export ANDROID_HOME=/root/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin
```

### 2. Script Permissions

Make all Ubuntu scripts executable:

```bash
chmod +x scripts/*.sh
```

### 3. Build Verification

Test the build environment:

```bash
# Test build (requires device connection)
bash scripts/test-build.sh

# Release build (requires keystore setup)
bash scripts/release-build.sh
```

## Docker Development

For containerized Ubuntu development:

```bash
# Create cache directories
mkdir -p docker-cache/gradle docker-cache/android-sdk APKFILE

# Start development container
docker compose up fastpay-android -d

# Enter container
docker exec -it fastpay-android-dev bash
```

## Known Issues & Solutions

### Firebase Configuration

The project requires `google-services.json` for Firebase integration. For development without Firebase:

1. The Firebase plugins are temporarily disabled in `app/build.gradle.kts`
2. Create `google-services.json.template` as a placeholder
3. Re-enable plugins when Firebase configuration is available

### Build Configuration Issues

- **Configuration Cache**: Use `--no-configuration-cache` flag if cache issues occur
- **Gradle Daemon**: Use `./gradlew --stop` to reset daemon if locks occur
- **Memory**: Ensure at least 4GB RAM for builds

### Ubuntu-Specific Considerations

- **File Permissions**: Scripts may need executable permissions
- **Path Issues**: Use absolute paths for Android SDK
- **Package Management**: Use apt for system dependencies

## Development Workflow

### 1. Initial Setup

```bash
# Clone and navigate
git clone <repository-url>
cd FASTPAY_APK

# Set environment
echo 'export ANDROID_HOME=/root/android-sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin' >> ~/.bashrc
source ~/.bashrc

# Make scripts executable
chmod +x scripts/*.sh
```

### 2. Daily Development

```bash
# Build and test
bash scripts/test-build.sh

# View logs (if device connected)
adb logcat -s ActivationActivity:D

# Clean build if needed
cd FASTPAY_BASE && ./gradlew clean assembleDebug
```

### 3. Release Process

```bash
# Setup keystore (first time)
cp FASTPAY_BASE/keystore.properties.template FASTPAY_BASE/keystore.properties
# Edit keystore.properties with your signing details

# Build release
bash scripts/release-build.sh
```

## Troubleshooting

### Build Failures

1. **Missing google-services.json**: Temporarily disable Firebase plugins
2. **Gradle issues**: Stop daemon and clean: `./gradlew --stop && ./gradlew clean`
3. **Permission issues**: Check script permissions and file ownership

### Docker Issues

1. **Build failures**: Check Android SDK download URL in Dockerfile
2. **Permission issues**: Ensure proper user mapping in docker-compose.yml
3. **Cache issues**: Clear docker volumes: `docker compose down -v`

### Device Connection

1. **ADB not found**: Check ANDROID_HOME and PATH variables
2. **Device not authorized**: Run `adb devices` and authorize on device
3. **Connection issues**: Restart ADB: `adb kill-server && adb start-server`

## Performance Optimization

### Gradle Configuration

- Use Gradle daemon for faster builds
- Enable configuration cache (when stable)
- Use incremental builds

### System Resources

- Minimum 4GB RAM recommended
- SSD storage improves build times
- Multiple CPU cores speed up compilation

## IDE Integration

### Android Studio

1. Open FASTPAY_APK as project root
2. Configure SDK location in settings
3. Use terminal for script execution

### VS Code/Cursor

1. Install Android extensions
2. Configure JAVA_HOME and ANDROID_HOME
3. Use integrated terminal for builds

## Testing

### Unit Tests

```bash
# Run unit tests
bash scripts/test-unit.sh

# From FASTPAY_BASE
./gradlew test
```

### Device Testing

```bash
# Install debug APK
bash scripts/test-build.sh

# View logs
adb logcat -s FastPay:D ActivationActivity:D
```

## CI/CD Integration

The project includes GitHub Actions workflows that work with Ubuntu:

- `.github/workflows/android-ci.yml` for CI
- Docker support for consistent environments
- Script-based builds for reproducibility

## Support

For Ubuntu-specific issues:

1. Check this guide first
2. Review `docs/07-environment.md` for general setup
3. Examine script files in `scripts/` directory
4. Check Docker logs if using containerized development
