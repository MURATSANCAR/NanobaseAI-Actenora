-- Schema-per-bounded-context baseline (docs/architecture/DATA-OWNERSHIP.md)
-- Authoritative table DDL arrives via module Flyway migrations.
-- This init only ensures schemas exist for local Postgres bootstrap.

CREATE SCHEMA IF NOT EXISTS identity AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS tenant AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS policy AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS microsoftconnection AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS meeting AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS transcript AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS modelmanagement AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS aiprocessing AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS meetingintelligence AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS approval AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS template AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS delivery AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS audit AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS operations AUTHORIZATION CURRENT_USER;

COMMENT ON SCHEMA identity IS 'Owned by identity module';
COMMENT ON SCHEMA tenant IS 'Owned by tenant module';
COMMENT ON SCHEMA policy IS 'Owned by policy module';
COMMENT ON SCHEMA microsoftconnection IS 'Owned by microsoft-connection module';
COMMENT ON SCHEMA meeting IS 'Owned by meeting module';
COMMENT ON SCHEMA transcript IS 'Owned by transcript module';
COMMENT ON SCHEMA modelmanagement IS 'Owned by model-management module';
COMMENT ON SCHEMA aiprocessing IS 'Owned by ai-processing module';
COMMENT ON SCHEMA meetingintelligence IS 'Owned by meeting-intelligence module';
COMMENT ON SCHEMA approval IS 'Owned by approval module';
COMMENT ON SCHEMA template IS 'Owned by template module';
COMMENT ON SCHEMA delivery IS 'Owned by delivery module';
COMMENT ON SCHEMA audit IS 'Owned by audit module (append-only)';
COMMENT ON SCHEMA operations IS 'Owned by operations module';

ALTER ROLE CURRENT_USER SET search_path TO public,
    identity, tenant, policy, microsoftconnection, meeting, transcript,
    modelmanagement, aiprocessing, meetingintelligence, approval, template,
    delivery, audit, operations;
