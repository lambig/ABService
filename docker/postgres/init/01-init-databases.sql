-- Initialize databases for ABService
-- This script creates the necessary databases and users

-- Create Keycloak database and user
CREATE DATABASE keycloak;
CREATE USER keycloak WITH PASSWORD 'keycloak';
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;

-- Create ABService database and user (if not exists)
-- Note: The main database is already created by POSTGRES_DB environment variable
-- This is just to ensure the user has proper permissions

-- Grant additional permissions to abservice user
GRANT ALL PRIVILEGES ON DATABASE abservice TO abservice;

-- Create extensions that might be useful
\c abservice;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Set timezone
SET timezone = 'UTC';
