#!/usr/bin/env bash
# Send a branded Nanobase meeting-note test email (HTML + PDF) via production SMTP.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
ensure_path_tools

BASE_URL="${ACTENORA_TEST_MAIL_BASE_URL:-https://portal.nanobase.ai}"
TO="${ACTENORA_TEST_MAIL_TO:-muratsancar@nanobase.ai}"

require_cmd curl

log "POST ${BASE_URL}/api/v1/delivery/test/meeting-note → ${TO}"
curl -sf -X POST "${BASE_URL}/api/v1/delivery/test/meeting-note" \
  -H "Content-Type: application/json" \
  -H "X-Actenora-Entra-Oid: ${VITE_IDENTITY_ENTRA_OID:?VITE_IDENTITY_ENTRA_OID required}" \
  -H "X-Actenora-Entra-Tid: ${VITE_IDENTITY_ENTRA_TID:?VITE_IDENTITY_ENTRA_TID required}" \
  -H "X-Actenora-Email: ${VITE_IDENTITY_EMAIL:?VITE_IDENTITY_EMAIL required}" \
  -H "X-Actenora-Display-Name: ${VITE_IDENTITY_DISPLAY_NAME:-Nanobase Operator}" \
  -H "X-Actenora-Global-Admin: true" \
  -d "{\"to\":\"${TO}\",\"useSample\":true}"

echo
log "Test meeting note mail queued/sent to ${TO}"
