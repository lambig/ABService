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
    
    # Make Gradle wrapper executable
    chmod +x gradlew
    
    # Install dependencies
    ./gradlew build -x test
    
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
    
    # Create data directories
    mkdir -p docker/postgres/data
    mkdir -p docker/keycloak/data
    mkdir -p docker/redis/data
    mkdir -p docker/minio/data
    
    # Start Docker services
    docker-compose up -d
    
    # Wait for services to be ready
    echo "⏳ Waiting for services to be ready..."
    sleep 10
    
    # Check service health
    echo "🔍 Checking service health..."
    docker-compose ps
    
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
    echo "  3. For development with management tools: npm run docker:up:dev"
    echo "  4. Check CONTRIBUTION.md for development guidelines"
    echo ""
    echo "🌐 Service URLs:"
    echo "  PostgreSQL: localhost:5432"
    echo "  Keycloak: http://localhost:8180"
    echo "  Mailhog: http://localhost:8025"
    echo "  MinIO: http://localhost:9001"
    echo "  pgAdmin: http://localhost:5050 (dev only)"
    echo "  Redis Commander: http://localhost:8081 (dev only)"
    echo ""
}

# Run main function
main "$@"
