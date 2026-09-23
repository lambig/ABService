#!/bin/sh
# Runs only for an empty PostgreSQL volume. Values are passed as psql variables,
# never interpolated into SQL by the shell. The application owns its DB/schema,
# but cannot create roles/databases or bypass access controls as a superuser.
set -eu
psql --username postgres --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 <<'SQL'
\getenv app_user APP_DB_USER
\getenv app_password APP_DB_PASSWORD
\getenv app_database POSTGRES_DB
CREATE ROLE :"app_user" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'app_password';
ALTER DATABASE :"app_database" OWNER TO :"app_user";
REVOKE ALL ON DATABASE :"app_database" FROM PUBLIC;
ALTER SCHEMA public OWNER TO :"app_user";
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SQL
