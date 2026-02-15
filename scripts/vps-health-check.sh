#!/bin/bash
# FASTPAY VPS Health Check Script
# Validates system resources, installations, and build capability
# Usage: bash scripts/vps-health-check.sh [--verbose]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Verbose mode
VERBOSE=false
if [ "$1" = "--verbose" ]; then
    VERBOSE=true
fi

# Counters
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
WARNING_CHECKS=0

# Logging functions
log() {
    echo -e "${BLUE}[HEALTH]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASSED_CHECKS++))
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
    ((WARNING_CHECKS++))
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((FAILED_CHECKS++))
}

check() {
    ((TOTAL_CHECKS++))
    if [ "$VERBOSE" = true ]; then
        log "Checking: $1"
    fi
}

# System resource checks
check_system_resources() {
    log "Checking system resources..."
    
    # RAM check
    check "System RAM"
    TOTAL_RAM=$(free -m | awk 'NR==2{print $2}')
    AVAILABLE_RAM=$(free -m | awk 'NR==2{print $7}')
    
    if [ "$TOTAL_RAM" -lt 2048 ]; then
        log_error "Low RAM: ${TOTAL_RAM}MB total, ${AVAILABLE_RAM}MB available (minimum 2GB recommended)"
    elif [ "$TOTAL_RAM" -lt 4096 ]; then
        log_warning "Moderate RAM: ${TOTAL_RAM}MB total, ${AVAILABLE_RAM}MB available (4GB+ recommended)"
    else
        log_success "Sufficient RAM: ${TOTAL_RAM}MB total, ${AVAILABLE_RAM}MB available"
    fi
    
    # CPU check
    check "CPU cores"
    CPU_CORES=$(nproc)
    if [ "$CPU_CORES" -lt 2 ]; then
        log_warning "Single CPU core detected (2+ recommended for faster builds)"
    else
        log_success "$CPU_CORES CPU cores detected"
    fi
    
    # Disk space check
    check "Disk space"
    FREE_DISK=$(df -BG / | awk 'NR==2{print $4}' | sed 's/G//')
    if [ "$FREE_DISK" -lt 10 ]; then
        log_error "Low disk space: ${FREE_DISK}GB free (minimum 10GB required)"
    elif [ "$FREE_DISK" -lt 20 ]; then
        log_warning "Moderate disk space: ${FREE_DISK}GB free (20GB+ recommended)"
    else
        log_success "Sufficient disk space: ${FREE_DISK}GB free"
    fi
    
    if [ "$VERBOSE" = true ]; then
        # Detailed system info
        log "System details:"
        echo "  OS: $(uname -s) $(uname -r)"
        echo "  Architecture: $(uname -m)"
        echo "  Uptime: $(uptime -p 2>/dev/null || uptime)"
        echo "  Load average: $(uptime | awk -F'load average:' '{print $2}')"
    fi
}

# Java installation check
check_java() {
    log "Checking Java installation..."
    
    check "Java installation"
    if ! command -v java &> /dev/null; then
        log_error "Java not found in PATH"
        return
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    JAVA_MAJOR=$(echo "$JAVA_VERSION" | cut -d'.' -f1)
    
    if [ "$JAVA_MAJOR" -lt 17 ]; then
        log_error "Java version $JAVA_VERSION is too old (Java 17+ required)"
    else
        log_success "Java $JAVA_VERSION installed"
    fi
    
    # Check JAVA_HOME
    check "JAVA_HOME environment"
    if [ -z "$JAVA_HOME" ]; then
        JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
        log_warning "JAVA_HOME not set, detected: $JAVA_HOME"
    else
        log_success "JAVA_HOME set to: $JAVA_HOME"
    fi
    
    if [ "$VERBOSE" = true ]; then
        log "Java details:"
        echo "  Java home: $JAVA_HOME"
        echo "  Java path: $(which java)"
        echo "  Javac path: $(which javac 2>/dev/null || echo 'not found')"
    fi
}

# Android SDK check
check_android_sdk() {
    log "Checking Android SDK..."
    
    # Check ANDROID_HOME
    check "ANDROID_HOME environment"
    if [ -z "$ANDROID_HOME" ]; then
        log_error "ANDROID_HOME not set"
        return
    fi
    
    if [ ! -d "$ANDROID_HOME" ]; then
        log_error "ANDROID_HOME directory not found: $ANDROID_HOME"
        return
    fi
    
    log_success "ANDROID_HOME set to: $ANDROID_HOME"
    
    # Check SDK components
    check "Android SDK tools"
    if [ ! -f "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
        log_error "Android SDK command-line tools not found"
    else
        log_success "Android SDK command-line tools found"
    fi
    
    # Check platform-tools
    check "Android platform-tools"
    if [ ! -f "$ANDROID_HOME/platform-tools/adb" ]; then
        log_error "ADB not found in platform-tools"
    else
        log_success "ADB found in platform-tools"
    fi
    
    # Check required platforms
    check "Android API 36 platform"
    if [ ! -d "$ANDROID_HOME/platforms/android-36" ]; then
        log_error "Android API 36 platform not installed"
    else
        log_success "Android API 36 platform installed"
    fi
    
    # Check build tools
    check "Android build-tools 36.0.0"
    if [ ! -d "$ANDROID_HOME/build-tools/36.0.0" ]; then
        log_error "Android build-tools 36.0.0 not installed"
    else
        log_success "Android build-tools 36.0.0 installed"
    fi
    
    if [ "$VERBOSE" = true ]; then
        log "Android SDK details:"
        echo "  SDK path: $ANDROID_HOME"
        echo "  ADB version: $(adb version 2>/dev/null | head -n1 || echo 'unknown')"
        echo "  Installed platforms: $(ls -1 $ANDROID_HOME/platforms/ 2>/dev/null | tr '\n' ' ' || echo 'none')"
        echo "  Installed build-tools: $(ls -1 $ANDROID_HOME/build-tools/ 2>/dev/null | tr '\n' ' ' || echo 'none')"
    fi
}

# Gradle wrapper check
check_gradle() {
    log "Checking Gradle wrapper..."
    
    # Find FASTPAY_BASE directory
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    REPO_ROOT="$(dirname "$SCRIPT_DIR")"
    FASTPAY_BASE="$REPO_ROOT/FASTPAY_BASE"
    
    check "FASTPAY_BASE directory"
    if [ ! -d "$FASTPAY_BASE" ]; then
        log_error "FASTPAY_BASE directory not found: $FASTPAY_BASE"
        return
    fi
    
    log_success "FASTPAY_BASE directory found"
    
    # Check gradlew
    check "Gradle wrapper"
    if [ ! -f "$FASTPAY_BASE/gradlew" ]; then
        log_error "Gradle wrapper not found"
        return
    fi
    
    if [ ! -x "$FASTPAY_BASE/gradlew" ]; then
        log_error "Gradle wrapper not executable"
        return
    fi
    
    log_success "Gradle wrapper found and executable"
    
    # Check Gradle version
    check "Gradle wrapper functionality"
    cd "$FASTPAY_BASE"
    if ./gradlew --version > /dev/null 2>&1; then
        GRADLE_VERSION=$(./gradlew --version 2>/dev/null | grep "Gradle" | head -n1 | awk '{print $2}')
        log_success "Gradle wrapper working (version $GRADLE_VERSION)"
    else
        log_error "Gradle wrapper not working"
    fi
    cd "$REPO_ROOT"
    
    if [ "$VERBOSE" = true ]; then
        log "Gradle details:"
        echo "  Gradle wrapper: $FASTPAY_BASE/gradlew"
        echo "  Gradle version: $GRADLE_VERSION"
        echo "  Gradle user home: ${GRADLE_USER_HOME:-$HOME/.gradle}"
    fi
}

# Build capability test
check_build_capability() {
    log "Checking build capability..."
    
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    REPO_ROOT="$(dirname "$SCRIPT_DIR")"
    FASTPAY_BASE="$REPO_ROOT/FASTPAY_BASE"
    
    if [ ! -d "$FASTPAY_BASE" ]; then
        log_warning "Skipping build test - FASTPAY_BASE not found"
        return
    fi
    
    check "Gradle daemon"
    cd "$FASTPAY_BASE"
    
    # Stop any existing daemon
    ./gradlew --stop > /dev/null 2>&1 || true
    
    # Try to get help (quick test)
    if timeout 30 ./gradlew --help > /dev/null 2>&1; then
        log_success "Gradle daemon responsive"
    else
        log_warning "Gradle daemon not responding (may need more time or resources)"
    fi
    
    # Test compilation (quick check)
    check "Basic compilation test"
    if timeout 120 ./gradlew compileDebugKotlin --no-daemon > /dev/null 2>&1; then
        log_success "Basic compilation test passed"
    else
        log_warning "Basic compilation test failed (may need full build setup)"
    fi
    
    cd "$REPO_ROOT"
}

# Network connectivity check
check_network() {
    log "Checking network connectivity..."
    
    check "Internet connectivity"
    if ping -c 1 google.com &> /dev/null || ping -c 1 8.8.8.8 &> /dev/null; then
        log_success "Internet connectivity working"
    else
        log_warning "Limited internet connectivity"
    fi
    
    # Check Maven repositories
    check "Maven repository access"
    if curl -s --connect-timeout 10 https://repo.maven.apache.org/maven2/ > /dev/null; then
        log_success "Maven repositories accessible"
    else
        log_warning "Maven repositories not accessible (may affect dependency downloads)"
    fi
    
    # Check Google Maven
    check "Google Maven repository"
    if curl -s --connect-timeout 10 https://dl.google.com/dl/android/maven2/ > /dev/null; then
        log_success "Google Maven repository accessible"
    else
        log_warning "Google Maven repository not accessible (may affect Android dependencies)"
    fi
}

# Environment configuration check
check_environment() {
    log "Checking environment configuration..."
    
    # Check PATH
    check "PATH includes Android tools"
    if echo "$PATH" | grep -q "android-sdk"; then
        log_success "PATH includes Android SDK tools"
    else
        log_warning "PATH may not include Android SDK tools"
    fi
    
    # Check environment files
    check "Environment configuration files"
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    REPO_ROOT="$(dirname "$SCRIPT_DIR")"
    
    if [ -f "$REPO_ROOT/FASTPAY_BASE/.env.example" ]; then
        log_success "Environment template found"
        
        if [ -f "$REPO_ROOT/FASTPAY_BASE/.env" ]; then
            log_success "Environment file configured"
        else
            log_warning "Environment file not configured (copy from .env.example)"
        fi
    else
        log_warning "Environment template not found"
    fi
    
    # Check keystore
    if [ -f "$REPO_ROOT/FASTPAY_BASE/keystore.properties" ]; then
        log_success "Keystore properties configured"
    elif [ -f "$REPO_ROOT/FASTPAY_BASE/keystore.properties.template" ]; then
        log_warning "Keystore template found but not configured"
    fi
}

# Generate report
generate_report() {
    echo ""
    log "Health Check Report"
    echo "=================="
    echo "Total checks: $TOTAL_CHECKS"
    echo "Passed: $PASSED_CHECKS"
    echo "Warnings: $WARNING_CHECKS"
    echo "Failed: $FAILED_CHECKS"
    echo ""
    
    if [ "$FAILED_CHECKS" -eq 0 ]; then
        if [ "$WARNING_CHECKS" -eq 0 ]; then
            log_success "🎉 All checks passed! System is ready for Android development."
        else
            log_warning "⚠️  System is functional but has $WARNING_CHECKS warnings."
        fi
    else
        log_error "❌ $FAILED_CHECKS critical issues found. Please resolve before proceeding."
        echo ""
        echo "Common fixes:"
        echo "- Set ANDROID_HOME environment variable"
        echo "- Install missing Android SDK components"
        echo "- Fix Gradle wrapper permissions"
        echo "- Check system resources (RAM, disk space)"
    fi
    
    echo ""
    echo "Next steps:"
    echo "1. Fix any failed checks above"
    echo "2. Run: bash scripts/release-build.sh"
    echo "3. Check docs/vps-setup.md for detailed instructions"
}

# Main function
main() {
    log "Starting FASTPAY VPS health check..."
    echo ""
    
    check_system_resources
    check_java
    check_android_sdk
    check_gradle
    check_build_capability
    check_network
    check_environment
    
    generate_report
    
    # Exit with appropriate code
    if [ "$FAILED_CHECKS" -gt 0 ]; then
        exit 1
    elif [ "$WARNING_CHECKS" -gt 0 ]; then
        exit 2
    else
        exit 0
    fi
}

# Run main function
main "$@"
