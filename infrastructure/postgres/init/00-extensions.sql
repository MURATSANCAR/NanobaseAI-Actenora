-- Actenora PostgreSQL bootstrap (FAZ 2 / Wave 0)
-- Runs once on first volume initialization via /docker-entrypoint-initdb.d
-- Extensions live in schema "extensions" so public is not polluted for Flyway
-- empty-schema / baseline checks. App search_path includes extensions.

CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA extensions;
-- Hybrid knowledge search (FTS + cosine). Requires pgvector-capable Postgres image.
-- NOTE: the `vector` TYPE must live in a schema on the app's runtime search_path
-- (public, <module schemas> — NOT `extensions`), otherwise Flyway migrations that
-- declare `embedding vector(N)` fail with `type "vector" does not exist` on a fresh
-- volume. Keep it in `public`. (Verified on server 2026-08-08: prod has it in public.)
CREATE EXTENSION IF NOT EXISTS "vector" WITH SCHEMA public;

DO $bootstrap$
BEGIN
  EXECUTE format(
    'ALTER DATABASE %I SET search_path TO public, extensions',
    current_database()
  );
END
$bootstrap$;
