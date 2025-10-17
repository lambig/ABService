#!/bin/bash

# ABService Development Script
# This script starts the development environment

set -e

echo "🚀 Starting ABService development environment..."

# Function to cleanup on exit
cleanup() {
    echo ""
    echo "🛑 Shutting down development environment..."
    # Kill all background processes
    jobs -p | xargs -r kill
    exit 0
}

# Set up signal handlers
trap cleanup SIGINT SIGTERM

# Start backend
start_backend() {
    echo "☕ Starting backend (Quarkus)..."
    cd backend
    ./gradlew quarkusDev &
    BACKEND_PID=$!
    cd ..
    echo "✅ Backend started (PID: $BACKEND_PID)"
}

# Start frontend-admin
start_frontend_admin() {
    echo "🎨 Starting frontend-admin (Svelte)..."
    cd frontend-admin
    npm run dev &
    ADMIN_PID=$!
    cd ..
    echo "✅ Frontend-admin started (PID: $ADMIN_PID)"
}

# Start frontend-public
start_frontend_public() {
    echo "🌐 Starting frontend-public (Svelte + Astro)..."
    cd frontend-public
    npm run dev &
    PUBLIC_PID=$!
    cd ..
    echo "✅ Frontend-public started (PID: $PUBLIC_PID)"
}

# Main development process
main() {
    echo "📋 Starting all services..."
    echo ""
    
    start_backend
    sleep 5  # Wait for backend to start
    
    start_frontend_admin
    start_frontend_public
    
    echo ""
    echo "🎉 All services started successfully!"
    echo ""
    echo "📚 Service URLs:"
    echo "  Backend API: http://localhost:8080"
    echo "  Frontend Admin: http://localhost:5173"
    echo "  Frontend Public: http://localhost:4321"
    echo ""
    echo "Press Ctrl+C to stop all services"
    echo ""
    
    # Wait for all background processes
    wait
}

# Run main function
main "$@"
