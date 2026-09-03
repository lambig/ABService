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

-- テスト用のデータベース（#252）
-- 統合テストと E2E は開発用（abservice）と別の場所へ書く。テストの初期化が開発中のデータを消さず、
-- 逆に開発中に作ったデータがテストの母集団へ混ざることもない。中身は実行のたびに作り直される。
CREATE DATABASE abservice_test;
GRANT ALL PRIVILEGES ON DATABASE abservice_test TO abservice;
\c abservice_test;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE DATABASE abservice_e2e;
GRANT ALL PRIVILEGES ON DATABASE abservice_e2e TO abservice;
\c abservice_e2e;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Set timezone
SET timezone = 'UTC';
