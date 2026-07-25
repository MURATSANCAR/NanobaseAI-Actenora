-- Actenora PostgreSQL bootstrap (FAZ 2)
-- Runs once on first volume initialization via /docker-entrypoint-initdb.d

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
