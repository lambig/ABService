#!/bin/bash

# ABService Setup Script
# This script sets up the development environment for ABService

set -e

echo "🚀 Setting up ABService development environment..."

# Check if required tools are installed
check_requirements() {
    echo "📋 Checking requirements..."
    
    if ! command -v java &> /dev/null; then
        echo "❌ Java is not installed. Please install Java 25 or later."
        exit 1
    fi
    
    if ! command -v node &> /dev/null; then
        echo "❌ Node.js is not installed. Please install Node.js 18 or later."
        exit 1
    fi
    
    if ! command -v docker &> /dev/null; then
        echo "❌ Docker is not installed. Please install Docker."
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        echo "❌ Docker Compose is not installed. Please install Docker Compose."
        exit 1
    fi
    
    echo "✅ All requirements are satisfied."
}

# Install root dependencies
install_root_deps() {
    echo "📦 Installing root dependencies..."
    npm install
}

# Setup backend
setup_backend() {
    echo "☕ Setting up backend (Quarkus)..."
    cd backend
    
    # Make Maven wrapper executable
    chmod +x mvnw
    
    # Install dependencies
    ./mvnw clean install -DskipTests
    
    cd ..
    echo "✅ Backend setup completed."
}

# Setup frontend-admin
setup_frontend_admin() {
    echo "🎨 Setting up frontend-admin (Svelte)..."
    cd frontend-admin
    
    # Install dependencies
    npm install
    
    cd ..
    echo "✅ Frontend-admin setup completed."
}

# Setup frontend-public
setup_frontend_public() {
    echo "🌐 Setting up frontend-public (Svelte + Astro)..."
    cd frontend-public
    
    # Install dependencies
    npm install
    
    cd ..
    echo "✅ Frontend-public setup completed."
}

# Setup Docker environment
setup_docker() {
    echo "🐳 Setting up Docker environment..."
    
    # Build Docker images
    docker-compose build
    
    echo "✅ Docker environment setup completed."
}

# Main setup process
main() {
    check_requirements
    install_root_deps
    setup_backend
    setup_frontend_admin
    setup_frontend_public
    setup_docker
    
    echo ""
    echo "🎉 ABService setup completed successfully!"
    echo ""
    echo "📚 Next steps:"
    echo "  1. Start development environment: npm run dev"
    echo "  2. Or start with Docker: npm run docker:up"
    echo "  3. Check CONTRIBUTION.md for development guidelines"
    echo ""
}

# Run main function
main "$@"
