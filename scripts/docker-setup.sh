#!/bin/bash
# Docker setup script for FASTPAY VPS development
# Sets up Docker environment and prepares volumes

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() {
    echo -e "${BLUE}[DOCKER]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Install Docker if not present
install_docker() {
    if ! command -v docker &> /dev/null; then
        log "Installing Docker..."
        
        # Detect distribution
        if [ -f /etc/os-release ]; then
            . /etc/os-release
            DISTRO=$ID
        else
            DISTRO="unknown"
        fi
        
        case "$DISTRO" in
            ubuntu|debian)
                # Install Docker
                sudo apt update
                sudo apt install -y apt-transport-https ca-certificates curl gnupg lsb-release
                
                # Add Docker's official GPG key
                curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
                
                # Set up stable repository
                echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
                
                # Install Docker Engine
                sudo apt update
                sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
                ;;
            centos|rhel|amzn)
                # Install Docker on CentOS/RHEL
                sudo yum install -y yum-utils
                sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
                sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
                ;;
            *)
                log_warning "Unsupported distribution for automatic Docker installation"
                log "Please install Docker manually: https://docs.docker.com/get-docker/"
                exit 1
                ;;
        esac
        
        # Start and enable Docker
        sudo systemctl start docker
        sudo systemctl enable docker
        
        # Add user to docker group
        sudo usermod -aG docker $USER
        log_warning "You may need to log out and log back in for Docker group changes to take effect"
    fi
}

# Install Docker Compose if not present
install_docker_compose() {
    if ! command -v docker-compose &> /dev/null; then
        log "Installing Docker Compose..."
        
        # Try to install via package manager first
        if command -v apt &> /dev/null; then
            sudo apt install -y docker-compose
        elif command -v yum &> /dev/null; then
            sudo yum install -y docker-compose
        else
            # Install manually
            sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
            sudo chmod +x /usr/local/bin/docker-compose
        fi
    fi
}

# Create cache directories
create_cache_dirs() {
    log "Creating Docker cache directories..."
    
    mkdir -p docker-cache/gradle
    mkdir -p docker-cache/android-sdk
    mkdir -p APKFILE
    
    # Set permissions
    chmod 755 docker-cache
    chmod 755 docker-cache/gradle
    chmod 755 docker-cache/android-sdk
    chmod 755 APKFILE
    
    log_success "Cache directories created"
}

# Build Docker images
build_images() {
    log "Building Docker images..."
    
    # Build Android development image
    docker-compose build fastpay-android
    
    log_success "Docker images built"
}

# Setup Docker environment
setup_docker_env() {
    log "Setting up Docker environment..."
    
    # Create .env file for Docker if not exists
    if [ ! -f .docker.env ]; then
        cat > .docker.env << EOF
# Docker environment variables
COMPOSE_PROJECT_NAME=fastpay
ANDROID_SDK_VERSION=36
BUILD_TOOLS_VERSION=36.0.0
JAVA_VERSION=17
EOF
        log_success "Docker environment file created"
    fi
}

# Validate Docker setup
validate_docker() {
    log "Validating Docker setup..."
    
    # Check Docker is running
    if ! docker info &> /dev/null; then
        log_error "Docker is not running"
        exit 1
    fi
    
    # Check Docker Compose
    if ! docker-compose --version &> /dev/null; then
        log_error "Docker Compose not working"
        exit 1
    fi
    
    # Test docker-compose file
    if ! docker-compose config &> /dev/null; then
        log_error "docker-compose.yml has syntax errors"
        exit 1
    fi
    
    log_success "Docker setup validated"
}

# Show usage instructions
show_usage() {
    echo ""
    log_success "Docker environment setup completed!"
    echo ""
    echo "🐳 Docker Commands:"
    echo "  docker-compose up -d                    # Start development environment"
    echo "  docker-compose down                     # Stop environment"
    echo "  docker-compose exec fastpay-android bash # Enter development container"
    echo ""
    echo "🔧 Development in Container:"
    echo "  docker-compose exec fastpay-android bash scripts/release-build.sh"
    echo "  docker-compose exec fastpay-android bash scripts/test-build.sh"
    echo ""
    echo "🌐 Web Service (optional):"
    echo "  docker-compose --profile web up -d     # Start with web demo"
    echo "  http://localhost:3000                   # Access web demos"
    echo ""
    echo "📁 Volumes:"
    echo "  ./docker-cache/gradle                  # Gradle cache"
    echo "  ./docker-cache/android-sdk              # Android SDK cache"
    echo "  ./APKFILE                               # Build outputs"
    echo ""
}

# Main function
main() {
    log "Setting up Docker environment for FASTPAY..."
    
    install_docker
    install_docker_compose
    create_cache_dirs
    setup_docker_env
    build_images
    validate_docker
    show_usage
}

# Run main function
main "$@"
