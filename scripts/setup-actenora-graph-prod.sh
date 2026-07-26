#!/usr/bin/env bash
# One-time Graph sandbox bootstrap on portal.nanobase.ai:
#   1) SQL-provision Actenora tenant bound to Entra tid
#   2) Smoke-test Graph webhook validation (public HTTPS)
#   3) Print curl steps for business context + calendar subscription (needs MSAL Bearer)
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
ensure_path_tools

SSH_HOST="${ACTENORA_BACKEND_SSH_HOST:-${ACTENORA_PORTAL_SSH_HOST:-nanobase}}"
REMOTE_ENV="${ACTENORA_BACKEND_ENV_FILE:-/etc/nanobaseai/actenora.env}"
BACKEND_HOST_PORT="${ACTENORA_PLATFORM_BACKEND_HOST_PORT:-8088}"
PUBLIC_BASE="${ACTENORA_PORTAL_PUBLIC_URL:-https://portal.nanobase.ai}"

ENTRA_TID="${ACTENORA_ENTRA_TID:-2d0c9d71-6ec8-4363-b5f7-4544ce7c7a27}"
TENANT_NAME="${ACTENORA_TENANT_NAME:-Nanobase}"
MAILBOX_UPN="${ACTENORA_MICROSOFT_GRAPH_DEFAULT_MAILBOX_USER_ID:-}"

read_env_var() {
  ssh "${SSH_HOST}" "sudo grep -E '^${1}=' '${REMOTE_ENV}' 2>/dev/null | head -1 | cut -d= -f2-"
}

CLIENT_STATE="$(read_env_var ACTENORA_MICROSOFT_GRAPH_CLIENT_STATE)"
if [[ -z "${CLIENT_STATE}" ]]; then
  die "ACTENORA_MICROSOFT_GRAPH_CLIENT_STATE missing in ${REMOTE_ENV}"
fi

if [[ -z "${MAILBOX_UPN}" ]]; then
  MAILBOX_UPN="$(read_env_var ACTENORA_MICROSOFT_GRAPH_DEFAULT_MAILBOX_USER_ID)"
fi

log "Provisioning Actenora tenant for Entra tid ${ENTRA_TID}"
ssh "${SSH_HOST}" bash -s <<EOF
set -euo pipefail
TENANT_ID=\$(sudo docker exec actenora-prodlike-postgres psql -U actenora -d actenora -tAc \\
  "SELECT id FROM tenant.tenants WHERE entra_tenant_id = '${ENTRA_TID}' LIMIT 1" | tr -d '[:space:]')
if [[ -z "\${TENANT_ID}" ]]; then
  TENANT_ID=\$(uuidgen | tr '[:upper:]' '[:lower:]')
  sudo docker exec actenora-prodlike-postgres psql -U actenora -d actenora -v ON_ERROR_STOP=1 -c \\
    "INSERT INTO tenant.tenants (id, name, status, timezone, default_language, retention_policy_days, entra_tenant_id, created_at, updated_at, version)
     VALUES ('\${TENANT_ID}', '${TENANT_NAME}', 'ACTIVE', 'Europe/Istanbul', 'tr', 365, '${ENTRA_TID}', NOW(), NOW(), 0);"
  echo "Created tenant \${TENANT_ID}"
else
  echo "Tenant already exists: \${TENANT_ID}"
fi
EOF

log "Webhook validation handshake (public)"
TOKEN="setup-$(date +%s)"
BODY="$(curl -sf "${PUBLIC_BASE}/api/v1/microsoft/webhooks/graph-notifications?validationToken=${TOKEN}")"
[[ "${BODY}" == "${TOKEN}" ]] || die "Webhook validation failed (got: ${BODY})"
log "OK Graph webhook reachable at ${PUBLIC_BASE}/api/v1/microsoft/webhooks/graph-notifications"

cat <<EOF

Next steps (MSAL Bearer from portal login — Global Administrator on first login):

1) Create business context:
   curl -sf -X POST '${PUBLIC_BASE}/api/v1/business-contexts' \\
     -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \\
     -d '{"name":"Default","description":"Graph production","referenceCode":"default"}'

2) Create calendar subscription:
   curl -sf -X POST '${PUBLIC_BASE}/api/v1/microsoft/subscriptions' \\
     -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \\
     -d '{
       "resource": "users/${MAILBOX_UPN:-YOUR_UPN}/events",
       "changeType": "created,updated,deleted",
       "notificationUrl": "${PUBLIC_BASE}/api/v1/microsoft/webhooks/graph-notifications",
       "lifecycleNotificationUrl": "${PUBLIC_BASE}/api/v1/microsoft/webhooks/graph-notifications",
       "clientState": "${CLIENT_STATE}",
       "expirationWindow": "PT48H"
     }'

3) Schedule a Teams meeting with transcription enabled on mailbox ${MAILBOX_UPN:-YOUR_UPN}.
   After the meeting ends, transcript poll + AI worker produce draft notes in the portal.

EOF
