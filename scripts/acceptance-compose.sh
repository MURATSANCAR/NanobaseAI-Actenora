#!/usr/bin/env bash
# Wave 7 — wait for prod-like compose health and probe API acceptance endpoints.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

COMPOSE_FILE="${COMPOSE_FILE:-${REPO_ROOT}/infrastructure/compose/docker-compose.prod-like.yml}"
ENV_FILE="${ENV_FILE:-${REPO_ROOT}/infrastructure/compose/.env.prod-fixture.example}"
BASE_URL="${BASE_URL:-http://127.0.0.1:${PLATFORM_BACKEND_PORT:-8080}}"
PORTAL_URL="${PORTAL_URL:-http://127.0.0.1:${WEB_PORTAL_PORT:-3000}}"
WAIT_TIMEOUT_SEC="${WAIT_TIMEOUT_SEC:-300}"
CHECK_PORTAL="${CHECK_PORTAL:-auto}"

require_cmd curl
require_cmd docker

if ! docker info >/dev/null 2>&1; then
  die "Docker daemon unavailable — start Docker and re-run"
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  die "Missing env file: ${ENV_FILE} (copy from infrastructure/compose/.env.prod-fixture.example)"
fi

# shellcheck disable=SC1090
set -a
source "${ENV_FILE}"
set +a

log "Waiting for platform-backend health (timeout ${WAIT_TIMEOUT_SEC}s)"
deadline=$((SECONDS + WAIT_TIMEOUT_SEC))
until curl -fsS "${BASE_URL}/actuator/health/liveness" | grep -q '"status":"UP"'; do
  if (( SECONDS >= deadline )); then
    docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" ps || true
    die "platform-backend liveness not UP within ${WAIT_TIMEOUT_SEC}s"
  fi
  sleep 3
done

log "GET /api/health"
curl -fsS "${BASE_URL}/api/health" | grep -q '"status":"UP"' \
  || die "/api/health did not report UP"

log "GET /actuator/health/liveness"
curl -fsS "${BASE_URL}/actuator/health/liveness" | grep -q '"status":"UP"' \
  || die "/actuator/health/liveness did not report UP"

if [[ "${SPRING_PROFILES_ACTIVE:-}" == *prod-fixture* ]]; then
  : "${VITE_IDENTITY_ENTRA_OID:?VITE_IDENTITY_ENTRA_OID required — real Entra object id, no canned fallback}"
  : "${VITE_IDENTITY_ENTRA_TID:?VITE_IDENTITY_ENTRA_TID required — real Entra directory id, no canned fallback}"
  : "${VITE_IDENTITY_EMAIL:?VITE_IDENTITY_EMAIL required — real work email, no canned fallback}"
  : "${VITE_IDENTITY_DISPLAY_NAME:?VITE_IDENTITY_DISPLAY_NAME required — real display name, no canned fallback}"
  log "GET /api/v1/portal/me (operator-supplied mock headers)"
  portal_me="$(
    curl -fsS \
      -H "Accept: application/json" \
      -H "X-Actenora-Entra-Oid: ${VITE_IDENTITY_ENTRA_OID}" \
      -H "X-Actenora-Entra-Tid: ${VITE_IDENTITY_ENTRA_TID}" \
      -H "X-Actenora-Email: ${VITE_IDENTITY_EMAIL}" \
      -H "X-Actenora-Display-Name: ${VITE_IDENTITY_DISPLAY_NAME}" \
      -H "X-Actenora-Global-Admin: ${VITE_IDENTITY_GLOBAL_ADMIN:-false}" \
      "${BASE_URL}/api/v1/portal/me"
  )"
  echo "${portal_me}" | grep -q '"email"' || die "/api/v1/portal/me missing email in response"
else
  warn "SPRING_PROFILES_ACTIVE lacks prod-fixture — skipping portal /me mock probe"
fi

if [[ "${CHECK_PORTAL}" == "true" ]] \
  || { [[ "${CHECK_PORTAL}" == "auto" ]] && docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" ps --services --filter "status=running" 2>/dev/null | grep -qx web-portal; }; then
  log "GET web-portal index (${PORTAL_URL})"
  curl -fsS "${PORTAL_URL}/" | grep -qi html || die "web-portal did not serve HTML"
fi

log "acceptance-compose passed"
