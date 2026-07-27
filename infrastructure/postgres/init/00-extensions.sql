-- Actenora PostgreSQL bootstrap (FAZ 2 / Wave 0)
-- Runs once on first volume initialization via /docker-entrypoint-initdb.d
-- Extensions live in schema "extensions" so public is not polluted for Flyway
-- empty-schema / baseline checks. App search_path includes extensions.

CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA extensions;
-- Hybrid knowledge search (FTS + cosine). Requires pgvector-capable Postgres image.
CREATE EXTENSION IF NOT EXISTS "vector" WITH SCHEMA extensions;

DO $bootstrap$
BEGIN
  EXECUTE format(
    'ALTER DATABASE %I SET search_path TO public, extensions',
    current_database()
  );
END
$bootstrap$;
