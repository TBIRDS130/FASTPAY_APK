#!/bin/bash
# FASTPAY VPS DEPLOYMENT SCRIPT - One-command complete setup
# Clones repository, sets up environment, and validates setup
# Usage: bash scripts/deploy-vps.sh [repository-url] [branch]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DEFAULT_REPO="https://github.com/your-username/FASTPAY_APK.git"
DEFAULT_BRANCH="main"
WORK_DIR="$HOME/fastpay-development"

# Logging functions
log() {
    echo -e "${BLUE}[DEPLOY]${NC} $1"
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

# Parse arguments
REPO_URL="${1:-$DEFAULT_REPO}"
BRANCH="${2:-$DEFAULT_BRANCH}"

# Check if running as root
check_user() {
    if [ "$EUID" -eq 0 ]; then
        log_warning "Running as root. Consider creating a non-root user for development."
        read -p "Continue as root? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

# Install git if not present
ensure_git() {
    if ! command -v git &> /dev/null; then
        log "Installing git..."
        if command -v apt &> /dev/null; then
            sudo apt update && sudo apt install -y git
        elif command -v dnf &> /dev/null; then
            sudo dnf install -y git
        elif command -v yum &> /dev/null; then
            sudo yum install -y git
        else
            log_error "Package manager not found. Please install git manually."
            exit 1
        fi
    fi
}

# Clone repository
clone_repository() {
    log "Cloning FASTPAY repository..."

    # Create work directory
    mkdir -p "$WORK_DIR"
    cd "$WORK_DIR"

    # Remove existing directory if present
    if [ -d "FASTPAY_APK" ]; then
        log_warning "Removing existing FASTPAY_APK directory..."
        rm -rf FASTPAY_APK
    fi

    # Clone repository
    if ! git clone -b "$BRANCH" "$REPO_URL"; then
        log_error "Failed to clone repository from $REPO_URL"
        exit 1
    fi

    cd FASTPAY_APK
    log_success "Repository cloned successfully"
}

# Run setup script
run_setup() {
    log "Running VPS setup script..."

    if [ ! -f "scripts/setup-vps.sh" ]; then
        log_error "Setup script not found. Please check the repository structure."
        exit 1
    fi

    # Make setup script executable
    chmod +x scripts/setup-vps.sh

    # Run setup
    if bash scripts/setup-vps.sh; then
        log_success "VPS setup completed"
    else
        log_error "VPS setup failed"
        exit 1
    fi
}

# Configure environment
configure_environment() {
    log "Configuring development environment..."

    # Setup environment file if template exists
    if [ -f "FASTPAY_BASE/.env.example" ] && [ ! -f "FASTPAY_BASE/.env" ]; then
        cp FASTPAY_BASE/.env.example FASTPAY_BASE/.env
        log_success "Environment file created from template"
        log_warning "Review and update FASTPAY_BASE/.env with your configuration"
    fi

    # Setup keystore template if exists
    if [ -f "FASTPAY_BASE/keystore.properties.template" ] && [ ! -f "FASTPAY_BASE/keystore.properties" ]; then
        log_warning "Keystore properties template found. Create keystore.properties for release builds."
    fi

    # Set correct permissions
    chmod +x scripts/*.sh
    chmod +x FASTPAY_BASE/gradlew

    log_success "Environment configuration completed"
}

# Validate installation
validate_installation() {
    log "Validating installation..."

    # Check if we're in the right directory
    if [ ! -f "scripts/release-build.sh" ]; then
        log_error "Not in FASTPAY_APK directory or scripts missing"
        exit 1
    fi

    # Check Android SDK
    if [ -z "$ANDROID_HOME" ]; then
        log_warning "ANDROID_HOME not set. Sourcing bash profile..."
        source ~/.bashrc
    fi

    if [ -z "$ANDROID_HOME" ]; then
        log_error "ANDROID_HOME still not set after sourcing profile"
        exit 1
    fi

    # Check Java
    if ! command -v java &> /dev/null; then
        log_error "Java not found"
        exit 1
    fi

    # Check Gradle wrapper
    if [ ! -x "FASTPAY_BASE/gradlew" ]; then
        log_error "Gradle wrapper not executable"
        exit 1
    fi

    log_success "Installation validation passed"
}

# Test build
test_build() {
    log "Running test build to validate setup..."

    cd FASTPAY_BASE

    # Stop any existing Gradle daemon
    ./gradlew --stop > /dev/null 2>&1 || true

    # Try debug build
    log "Building debug APK..."
    if ./gradlew assembleDebug --no-daemon --stacktrace; then
        log_success "Test build successful!"

        # Show build output
        APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)
        if [ -n "$APK_PATH" ]; then
            log_success "APK built: $APK_PATH"
            APK_SIZE=$(stat -c%s "$APK_PATH" 2>/dev/null || stat -f%z "$APK_PATH" 2>/dev/null || echo "unknown")
            log "APK size: $APK_SIZE bytes"
        fi
    else
        log_warning "Test build failed, but environment setup may still be successful"
        log "Check the error messages above for specific issues"
    fi

    cd ..
}

# Create development shortcuts
create_shortcuts() {
    log "Creating development shortcuts..."

    # Create alias for quick access
    echo "" >> ~/.bashrc
    echo "# FASTPAY Development Shortcuts" >> ~/.bashrc
    echo "alias fastpay='cd $WORK_DIR/FASTPAY_APK'" >> ~/.bashrc
    echo "alias fastpay-build='cd $WORK_DIR/FASTPAY_APK && bash scripts/release-build.sh'" >> ~/.bashrc
    echo "alias fastpay-test='cd $WORK_DIR/FASTPAY_APK && bash scripts/test-build.sh'" >> ~/.bashrc

    log_success "Development shortcuts created"
    log "Use 'fastpay' to go to project directory"
    log "Use 'fastpay-build' to build release APK"
    log "Use 'fastpay-test' to build debug APK"
}

# Show next steps
show_next_steps() {
    echo ""
    log_success "🎉 FASTPAY VPS deployment completed successfully!"
    echo ""
    echo "📁 Project location: $WORK_DIR/FASTPAY_APK"
    echo "🔧 Android SDK: $ANDROID_HOME"
    echo ""
    echo "🚀 Quick Start Commands:"
    echo "  fastpay                    # Go to project directory"
    echo "  fastpay-build             # Build release APK"
    echo "  fastpay-test              # Build debug APK"
    echo ""
    echo "📋 Next Steps:"
    echo "1. Reload your shell: source ~/.bashrc"
    echo "2. Configure FASTPAY_BASE/.env if needed"
    echo "3. Setup keystore.properties for release builds"
    echo "4. Run your first build: fastpay-build"
    echo ""
    echo "📚 Documentation:"
    echo "  docs/vps-setup.md         # Complete VPS guide"
    echo "  docs/02-build-and-run.md  # Build instructions"
    echo "  docs/07-environment.md   # Environment details"
    echo ""
    echo "🔍 Validation:"
    echo "  bash scripts/vps-health-check.sh  # System health check"
    echo ""
}

# Main deployment function
main() {
    log "Starting FASTPAY VPS deployment..."
    log "Repository: $REPO_URL"
    log "Branch: $BRANCH"
    log "Work Directory: $WORK_DIR"
    echo ""

    check_user
    ensure_git
    clone_repository
    run_setup
    configure_environment
    validate_installation
    test_build
    create_shortcuts
    show_next_steps
}

# Handle script interruption
trap 'log_error "Deployment interrupted"; exit 1' INT TERM

# Run main function
main "$@"
