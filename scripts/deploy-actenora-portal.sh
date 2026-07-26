#!/usr/bin/env bash
# Build Actenora web-portal and install under portal.nanobase.ai/actenora/
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
ensure_path_tools

SSH_HOST="${ACTENORA_PORTAL_SSH_HOST:-nanobase}"
INSTALL_DIR="${ACTENORA_PORTAL_INSTALL_DIR:-/data/nanobaseai-mobile/portal/dist/actenora}"
PORTAL_BASE="${ACTENORA_PORTAL_BASE:-/actenora/}"
PUBLIC_URL="${ACTENORA_PORTAL_PUBLIC_URL:-https://portal.nanobase.ai/actenora/}"

require_cmd pnpm
require_cmd rsync
require_cmd ssh

log "Building web-portal (base=${PORTAL_BASE})"
(
  cd "${REPO_ROOT}"
  # Optional: load portal Vite env from repo .env (MSAL, API URL, mock identity)
  if [[ -f "${REPO_ROOT}/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "${REPO_ROOT}/.env"
    set +a
  fi
  VITE_BASE="${PORTAL_BASE}" pnpm --filter @actenora/web-portal run build
)

STAGING="${ARTIFACTS_DIR}/actenora-portal-dist"
rm -rf "${STAGING}"
mkdir -p "${STAGING}"
rsync -a "${REPO_ROOT}/apps/web-portal/dist/" "${STAGING}/"

REMOTE_TMP="/tmp/actenora-portal-dist-$$"
log "Uploading to ${SSH_HOST}:${INSTALL_DIR}"
rsync -az --delete "${STAGING}/" "${SSH_HOST}:${REMOTE_TMP}/"
ssh "${SSH_HOST}" bash -s <<EOF
set -euo pipefail
sudo mkdir -p "${INSTALL_DIR}"
sudo rsync -a --delete "${REMOTE_TMP}/" "${INSTALL_DIR}/"
sudo chown -R administrator:administrator "${INSTALL_DIR}"
rm -rf "${REMOTE_TMP}"
EOF

if ssh "${SSH_HOST}" "curl -sf -o /dev/null -w '%{http_code}' '${PUBLIC_URL}'" | grep -q '^200$'; then
  log "Health check OK: ${PUBLIC_URL}"
else
  warn "Health check did not return 200 for ${PUBLIC_URL} — verify nginx location ^~ /actenora/"
fi

log "Actenora portal deploy complete → ${PUBLIC_URL}"
