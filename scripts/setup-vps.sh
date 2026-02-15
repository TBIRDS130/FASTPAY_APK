#!/bin/bash
# FASTPAY VPS SETUP SCRIPT - Complete zero-to-development environment
# Designed for fresh VPS with no prior installations
# Supports: Ubuntu/Debian, CentOS/RHEL, Amazon Linux
# Usage: bash scripts/setup-vps.sh

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${BLUE}[SETUP]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Detect Linux distribution
detect_distro() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        echo "$ID"
    elif [ -f /etc/redhat-release ]; then
        echo "rhel"
    else
        echo "unknown"
    fi
}

# Check system requirements
check_requirements() {
    log "Checking system requirements..."
    
    # Check RAM (minimum 2GB, recommended 4GB)
    TOTAL_RAM=$(free -m | awk 'NR==2{print $2}')
    if [ "$TOTAL_RAM" -lt 2048 ]; then
        log_warning "Low RAM detected: ${TOTAL_RAM}MB. Minimum recommended is 2GB."
    fi
    
    # Check disk space (minimum 10GB free)
    FREE_DISK=$(df -BG / | awk 'NR==2{print $4}' | sed 's/G//')
    if [ "$FREE_DISK" -lt 10 ]; then
        log_error "Insufficient disk space: ${FREE_DISK}GB free. Minimum required is 10GB."
        exit 1
    fi
    
    log_success "System requirements check passed"
}

# Update system packages
update_system() {
    log "Updating system packages..."
    DISTRO=$(detect_distro)
    
    case "$DISTRO" in
        ubuntu|debian)
            sudo apt update && sudo apt upgrade -y
            ;;
        centos|rhel|amzn)
            if command -v dnf &> /dev/null; then
                sudo dnf update -y
            else
                sudo yum update -y
            fi
            ;;
        *)
            log_error "Unsupported distribution: $DISTRO"
            exit 1
            ;;
    esac
    
    log_success "System packages updated"
}

# Install essential build tools
install_essentials() {
    log "Installing essential build tools..."
    DISTRO=$(detect_distro)
    
    case "$DISTRO" in
        ubuntu|debian)
            sudo apt install -y curl unzip git wget ca-certificates software-properties-common \
                build-essential lib32stdc++6 lib32z1 python3 python3-pip
            ;;
        centos|rhel|amzn)
            if command -v dnf &> /dev/null; then
                sudo dnf groupinstall -y "Development Tools"
                sudo dnf install -y curl unzip git wget ca-certificates python3 python3-pip
            else
                sudo yum groupinstall -y "Development Tools"
                sudo yum install -y curl unzip git wget ca-certificates python3 python3-pip
            fi
            ;;
    esac
    
    log_success "Essential tools installed"
}

# Install Java 17
install_java() {
    log "Installing Java 17..."
    DISTRO=$(detect_distro)
    
    case "$DISTRO" in
        ubuntu|debian)
            # Add OpenJDK repository if needed
            sudo apt install -y openjdk-17-jdk
            ;;
        centos|rhel|amzn)
            if command -v dnf &> /dev/null; then
                sudo dnf install -y java-17-openjdk-devel
            else
                sudo yum install -y java-17-openjdk-devel
            fi
            ;;
    esac
    
    # Verify Java installation
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    log_success "Java installed: $JAVA_VERSION"
}

# Install Android SDK
install_android_sdk() {
    log "Installing Android SDK..."
    
    # Create Android SDK directory
    ANDROID_HOME="$HOME/android-sdk"
    mkdir -p "$ANDROID_HOME"
    
    # Download Android command-line tools
    cd /tmp
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
    
    # Unzip and setup
    unzip -q cmdline-tools.zip
    mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
    mv cmdline-tools/* "$ANDROID_HOME/cmdline-tools/latest/" 2>/dev/null || true
    rm cmdline-tools.zip
    
    # Set environment variables
    echo "export ANDROID_HOME=$ANDROID_HOME" >> ~/.bashrc
    echo "export PATH=\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/build-tools/36.0.0:\$PATH" >> ~/.bashrc
    
    # Apply to current session
    export ANDROID_HOME="$ANDROID_HOME"
    export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0:$PATH"
    
    log_success "Android SDK installed to $ANDROID_HOME"
}

# Setup Android SDK components
setup_android_components() {
    log "Setting up Android SDK components..."
    
    # Accept all licenses
    yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1
    
    # Install required components
    "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-36" "build-tools;36.0.0"
    
    log_success "Android SDK components installed"
}

# Setup Gradle wrapper permissions
setup_gradle() {
    log "Setting up Gradle wrapper permissions..."
    
    # Find FASTPAY_BASE directory
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    REPO_ROOT="$(dirname "$SCRIPT_DIR")"
    FASTPAY_BASE="$REPO_ROOT/FASTPAY_BASE"
    
    if [ -d "$FASTPAY_BASE" ]; then
        chmod +x "$FASTPAY_BASE/gradlew"
        log_success "Gradle wrapper permissions set"
    else
        log_warning "FASTPAY_BASE directory not found. Will set permissions when repository is cloned."
    fi
}

# Setup environment variables permanently
setup_environment() {
    log "Setting up permanent environment variables..."
    
    # Add to .bashrc if not already present
    if ! grep -q "ANDROID_HOME" ~/.bashrc; then
        echo "" >> ~/.bashrc
        echo "# Android Development Environment" >> ~/.bashrc
        echo "export ANDROID_HOME=\$HOME/android-sdk" >> ~/.bashrc
        echo "export PATH=\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/build-tools/36.0.0:\$PATH" >> ~/.bashrc
        echo "export JAVA_HOME=\$(dirname \$(dirname \$(readlink -f \$(which java))))" >> ~/.bashrc
    fi
    
    log_success "Environment variables configured"
}

# Validate setup
validate_setup() {
    log "Validating setup..."
    
    # Check Java
    if ! command -v java &> /dev/null; then
        log_error "Java not found"
        return 1
    fi
    
    # Check Android SDK
    if [ ! -d "$ANDROID_HOME" ]; then
        log_error "Android SDK not found"
        return 1
    fi
    
    # Check ADB
    if ! command -v adb &> /dev/null; then
        log_error "ADB not found"
        return 1
    fi
    
    # Check SDK components
    if [ ! -d "$ANDROID_HOME/platforms/android-36" ]; then
        log_error "Android API 36 platform not installed"
        return 1
    fi
    
    log_success "Setup validation passed"
}

# Test build if FASTPAY_BASE exists
test_build() {
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    REPO_ROOT="$(dirname "$SCRIPT_DIR")"
    FASTPAY_BASE="$REPO_ROOT/FASTPAY_BASE"
    
    if [ -d "$FASTPAY_BASE" ] && [ -f "$FASTPAY_BASE/gradlew" ]; then
        log "Running test build..."
        cd "$FASTPAY_BASE"
        
        # Stop any existing Gradle daemon
        ./gradlew --stop > /dev/null 2>&1 || true
        
        # Try a simple build
        if ./gradlew assembleDebug --no-daemon; then
            log_success "Test build successful!"
        else
            log_warning "Test build failed, but environment is set up"
        fi
        cd "$REPO_ROOT"
    else
        log_warning "FASTPAY_BASE not found. Run test build after cloning repository."
    fi
}

# Main execution
main() {
    log "Starting FASTPAY VPS setup..."
    log "This script will install Android development environment on a fresh VPS"
    
    check_requirements
    update_system
    install_essentials
    install_java
    install_android_sdk
    setup_android_components
    setup_gradle
    setup_environment
    validate_setup
    test_build
    
    echo ""
    log_success "VPS setup completed successfully!"
    echo ""
    echo "Next steps:"
    echo "1. Reload your shell: source ~/.bashrc"
    echo "2. Clone the FASTPAY repository if not already done"
    echo "3. Run: bash scripts/release-build.sh"
    echo ""
    echo "Environment variables set in ~/.bashrc"
    echo "Android SDK installed at: $ANDROID_HOME"
    echo ""
}

# Run main function
main "$@"
