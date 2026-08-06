#!/usr/bin/env bash
# Run Postgres/Rabbit Testcontainers ITs on a host with Docker (e.g. nanobase).
# Local Macs without Docker skip these suites via disabledWithoutDocker=true.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"
ensure_path_tools

require_cmd docker
docker info >/dev/null 2>&1 || die "Docker daemon not available"

log "Running Testcontainers IT suites (Docker Engine 29+ needs api.version>=1.44)"
mvnw -pl modules/shared-kernel,apps/platform-backend -am \
  -Dtest=JdbcMessagingStoresPostgresTest,JdbcRabbitMessagingIntegrationTest,GraphJdbcEdgePersistenceTest \
  -DfailIfNoTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dapi.version=1.44 \
  test

log "Testcontainers IT suites finished"
