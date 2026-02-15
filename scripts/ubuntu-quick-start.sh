#!/bin/bash
# Ubuntu Quick Start Script for FASTPAY_APK
# Sets up Ubuntu environment for FastPay Android development

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() {
    echo -e "${BLUE}[SETUP]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Check if running from project root
if [ ! -f "README.md" ] || [ ! -d "FASTPAY_BASE" ]; then
    log_error "Please run this script from the FASTPAY_APK project root"
    exit 1
fi

log "Setting up Ubuntu development environment for FASTPAY_APK..."

# 1. Set environment variables
log "Setting up environment variables..."
if ! grep -q "ANDROID_HOME" ~/.bashrc; then
    echo 'export ANDROID_HOME=/root/android-sdk' >> ~/.bashrc
    echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin' >> ~/.bashrc
    log_success "Environment variables added to ~/.bashrc"
else
    log_warning "Environment variables already exist in ~/.bashrc"
fi

# Export for current session
export ANDROID_HOME=/root/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin

# 2. Make scripts executable
log "Making scripts executable..."
chmod +x scripts/*.sh
log_success "Scripts made executable"

# 3. Create necessary directories
log "Creating cache directories..."
mkdir -p docker-cache/gradle docker-cache/android-sdk APKFILE
log_success "Cache directories created"

# 4. Check Java installation
log "Checking Java installation..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    log_success "Java found: $JAVA_VERSION"
else
    log_warning "Java not found. Please install OpenJDK 17"
fi

# 5. Check Android SDK
log "Checking Android SDK..."
if [ -d "$ANDROID_HOME" ]; then
    log_success "Android SDK found at $ANDROID_HOME"
else
    log_warning "Android SDK not found at $ANDROID_HOME"
fi

# 6. Check ADB
log "Checking ADB..."
if command -v adb &> /dev/null; then
    ADB_VERSION=$(adb version | head -n 1)
    log_success "ADB found: $ADB_VERSION"
else
    log_warning "ADB not found in PATH"
fi

# 7. Check Docker
log "Checking Docker..."
if command -v docker &> /dev/null; then
    DOCKER_VERSION=$(docker --version)
    log_success "Docker found: $DOCKER_VERSION"
    
    # Check docker-compose
    if command -v docker-compose &> /dev/null; then
        log_success "Docker Compose available"
    else
        log_warning "Docker Compose not found"
    fi
else
    log_warning "Docker not found"
fi

# 8. Test Gradle wrapper
log "Testing Gradle wrapper..."
cd FASTPAY_BASE
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    log_success "Gradle wrapper found and made executable"
else
    log_warning "Gradle wrapper not found"
fi

cd ..

# 9. Create development shortcuts
log "Creating development shortcuts..."
cat > ~/fastpay-dev << 'EOF'
#!/bin/bash
# FastPay Development Helper Script

case "$1" in
    "build")
        echo "Building FastPay debug APK..."
        bash scripts/test-build.sh
        ;;
    "release")
        echo "Building FastPay release APK..."
        bash scripts/release-build.sh
        ;;
    "logs")
        echo "Showing FastPay logs..."
        adb logcat -s FastPay:D ActivationActivity:D ActivatedActivity:D PersistentForegroundService:D SplashActivity:D DebugLogger:D
        ;;
    "docker")
        echo "Starting Docker development environment..."
        docker compose up fastpay-android -d
        echo "Container started. Connect with: docker exec -it fastpay-android-dev bash"
        ;;
    "clean")
        echo "Cleaning build artifacts..."
        cd FASTPAY_BASE && ./gradlew clean && cd ..
        ;;
    *)
        echo "FastPay Development Helper"
        echo "Usage: fastpay-dev [command]"
        echo ""
        echo "Commands:"
        echo "  build    - Build debug APK"
        echo "  release  - Build release APK"
        echo "  logs     - Show device logs"
        echo "  docker  - Start Docker environment"
        echo "  clean    - Clean build artifacts"
        ;;
esac
EOF

chmod +x ~/fastpay-dev
log_success "Development helper created: ~/fastpay-dev"

# 10. Summary
log_success "Ubuntu environment setup complete!"
echo ""
echo "Quick commands:"
echo "  ~/fastpay-dev build     - Build debug APK"
echo "  ~/fastpay-dev release   - Build release APK" 
echo "  ~/fastpay-dev logs      - View device logs"
echo "  ~/fastpay-dev docker    - Start Docker environment"
echo "  ~/fastpay-dev clean     - Clean build artifacts"
echo ""
echo "Next steps:"
echo "1. Source ~/.bashrc or restart terminal"
echo "2. Connect Android device for testing"
echo "3. Run ~/fastpay-dev build to test the setup"
echo ""
echo "For detailed documentation, see: docs/ubuntu-development-guide.md"
