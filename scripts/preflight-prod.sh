#!/usr/bin/env bash
# Pre-flight checks for the single-file prod compose (docker-compose.prod.yml).
# Runs ALL checks (does not stop at first failure) and prints a PASS/WARN/FAIL
# summary. Exit 0 only if there are no FAILs.
#
# Usage:
#   ENV_FILE=/etc/nanobaseai/actenora.prod.env ./scripts/preflight-prod.sh
#   ./scripts/preflight-prod.sh /etc/nanobaseai/actenora.prod.env
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

COMPOSE_DIR="${REPO_ROOT}/infrastructure/compose"
COMPOSE_FILE="${COMPOSE_FILE:-${COMPOSE_DIR}/docker-compose.prod.yml}"
ENV_FILE="${1:-${ENV_FILE:-${COMPOSE_DIR}/.env.prod}}"

PASS=0; WARNC=0; FAILC=0
pass() { printf '  \033[32mPASS\033[0m %s\n' "$*"; PASS=$((PASS+1)); }
warnc() { printf '  \033[33mWARN\033[0m %s\n' "$*"; WARNC=$((WARNC+1)); }
failc() { printf '  \033[31mFAIL\033[0m %s\n' "$*"; FAILC=$((FAILC+1)); }
section() { printf '\n\033[1m%s\033[0m\n' "$*"; }

# --- 1. env file present & loadable ------------------------------------------
section "1. Environment file"
if [[ ! -f "${ENV_FILE}" ]]; then
  failc "env file not found: ${ENV_FILE}"
  printf '\nCannot continue without an env file. Copy the template:\n  cp %s/.env.prod.example %s\n' "${COMPOSE_DIR}" "${ENV_FILE}"
  exit 1
fi
pass "env file: ${ENV_FILE}"
# Parse WITHOUT sourcing: docker --env-file values may contain spaces and must not
# be executed as shell. (Comments belong on their own lines in this format.)
while IFS= read -r _line || [[ -n "${_line}" ]]; do
  _line="${_line%$'\r'}"
  [[ -z "${_line}" || "${_line}" =~ ^[[:space:]]*# ]] && continue
  [[ "${_line}" != *=* ]] && continue
  _key="${_line%%=*}"
  _val="${_line#*=}"
  _key="${_key//[[:space:]]/}"
  [[ "${_key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
  printf -v "${_key}" '%s' "${_val}"
  export "${_key}"
done < "${ENV_FILE}"

# --- 2. required variables non-empty -----------------------------------------
section "2. Required variables"
REQUIRED=(
  POSTGRES_PASSWORD RABBITMQ_PASSWORD
  OBJECT_STORAGE_ACCESS_KEY OBJECT_STORAGE_SECRET_KEY
  ACTENORA_ENTRA_ISSUER_URI ACTENORA_ENTRA_AUDIENCE ACTENORA_CORS_ALLOWED_ORIGINS
  VITE_API_BASE_URL VITE_ENTRA_CLIENT_ID VITE_ENTRA_TENANT_ID VITE_ENTRA_API_SCOPE
  ACTENORA_PORTAL_LINK_SECRET ACTENORA_DELIVERY_WEBHOOK_SECRET ACTENORA_DELIVERY_PORTAL_LINK_BASE_URL
  ACTENORA_MICROSOFT_GRAPH_TENANT_ID ACTENORA_MICROSOFT_GRAPH_CLIENT_ID ACTENORA_MICROSOFT_GRAPH_CLIENT_STATE
  ACTENORA_DELIVERY_GRAPH_SENDER ACTENORA_DELIVERY_MAIL_FROM
  ACTENORA_KNOWLEDGE_EMBEDDING_BASE_URL ACTENORA_KNOWLEDGE_EMBEDDING_MODEL_ID
)
for v in "${REQUIRED[@]}"; do
  if [[ -z "${!v:-}" ]]; then failc "${v} is empty"; else pass "${v} set"; fi
done

# CLIENT_SECRET mode needs the secret; CERTIFICATE mode needs the PEM pair.
GRAPH_AUTH="${ACTENORA_MICROSOFT_GRAPH_AUTH_MODE:-CERTIFICATE}"
if [[ "${GRAPH_AUTH}" == "CLIENT_SECRET" ]]; then
  [[ -n "${ACTENORA_MICROSOFT_GRAPH_CLIENT_SECRET:-}" ]] \
    && pass "ACTENORA_MICROSOFT_GRAPH_CLIENT_SECRET set (CLIENT_SECRET mode)" \
    || failc "ACTENORA_MICROSOFT_GRAPH_CLIENT_SECRET empty but AUTH_MODE=CLIENT_SECRET"
fi

# --- 3. weak / placeholder secrets -------------------------------------------
section "3. Secret hygiene (ProductionSecretGuard)"
WEAK=0
while IFS='=' read -r k val; do
  [[ "${k}" =~ ^# || -z "${k}" ]] && continue
  if [[ "${val}" == *change_me* || "${val}" == *actenora_local* ]]; then
    failc "${k} looks like a placeholder (${val})"; WEAK=1
  fi
done < "${ENV_FILE}"
[[ "${WEAK}" -eq 0 ]] && pass "no *_change_me / actenora_local placeholders"
[[ "${ACTENORA_ALLOW_DEFAULT_SECRETS:-false}" == "false" ]] \
  && pass "ACTENORA_ALLOW_DEFAULT_SECRETS not enabled" \
  || warnc "ACTENORA_ALLOW_DEFAULT_SECRETS=true — must be false in prod"

# --- 4. Graph certificate (CERTIFICATE mode) ---------------------------------
section "4. Microsoft Graph auth material (${GRAPH_AUTH})"
if [[ "${GRAPH_AUTH}" == "CERTIFICATE" ]]; then
  SECRETS_DIR="${ACTENORA_GRAPH_SECRETS_HOST_DIR:-./secrets/graph}"
  # resolve relative paths against the compose dir (that's where the mount is relative to)
  [[ "${SECRETS_DIR}" = /* ]] || SECRETS_DIR="${COMPOSE_DIR}/${SECRETS_DIR}"
  for pem in cert.pem key.pem; do
    if [[ -f "${SECRETS_DIR}/${pem}" ]]; then pass "graph ${pem} present"; else failc "missing ${SECRETS_DIR}/${pem}"; fi
  done
else
  pass "certificate not required in ${GRAPH_AUTH} mode"
fi

# --- 5. LLM + embedding reachability (best-effort) ---------------------------
section "5. LLM / embedding endpoints (best-effort)"
probe() { # $1=label $2=base-url
  local label="$1" url="$2"
  [[ -z "${url}" ]] && { warnc "${label}: no URL set"; return; }
  # From the host, host.docker.internal usually means localhost.
  local hosturl="${url/host.docker.internal/127.0.0.1}"
  if require_cmd curl >/dev/null 2>&1 && curl -fsS --max-time 5 "${hosturl%/}/v1/models" >/dev/null 2>&1; then
    pass "${label} reachable (${hosturl%/}/v1/models)"
  elif curl -fsS --max-time 5 "${hosturl%/}/health" >/dev/null 2>&1; then
    pass "${label} reachable (${hosturl%/}/health)"
  else
    warnc "${label} not reachable from host at ${hosturl} (may be fine if on another host / started later)"
  fi
}
probe "LLM provider" "${ACTENORA_AI_PROVIDER_BASE_URL:-}"
probe "Embedding" "${ACTENORA_KNOWLEDGE_EMBEDDING_BASE_URL:-}"

# --- 6. compose config validation --------------------------------------------
section "6. Compose file validation"
if docker_ok 2>/dev/null || { command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; }; then
  if docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" config >/dev/null 2>/tmp/actenora-compose-config.err; then
    pass "docker compose config valid"
  else
    failc "docker compose config failed:"; sed 's/^/       /' /tmp/actenora-compose-config.err >&2
  fi
else
  warnc "docker unavailable — skipped 'compose config' (run it on the server before up)"
fi

# --- 7. host ports free ------------------------------------------------------
section "7. Host ports"
port_free() { # $1=port $2=label
  if command -v lsof >/dev/null 2>&1 && lsof -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; then
    warnc "$2 port $1 already in use (ok if it's this stack restarting)"
  else
    pass "$2 port $1 free"
  fi
}
port_free "${ACTENORA_PLATFORM_BACKEND_HOST_PORT:-8088}" "backend"
port_free "${WEB_PORTAL_HOST_PORT:-3000}" "portal"

# --- summary -----------------------------------------------------------------
section "Summary"
printf '  %d passed, %d warnings, %d failed\n' "${PASS}" "${WARNC}" "${FAILC}"
if [[ "${FAILC}" -gt 0 ]]; then
  printf '\n\033[31mPre-flight FAILED\033[0m — fix the FAIL items before: make prod-up ENV_FILE=%s\n' "${ENV_FILE}"
  exit 1
fi
printf '\n\033[32mPre-flight OK\033[0m — ready for: make prod-up ENV_FILE=%s\n' "${ENV_FILE}"
[[ "${WARNC}" -gt 0 ]] && printf '(review the %d warning(s) above)\n' "${WARNC}"
exit 0
