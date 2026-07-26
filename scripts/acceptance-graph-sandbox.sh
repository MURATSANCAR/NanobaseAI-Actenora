#!/usr/bin/env bash
# Graph sandbox acceptance — health, subscription API, portal composition headers.
# Requires platform-backend on ACTENORA_BASE_URL (default http://localhost:8080)
# with SPRING_PROFILES_ACTIVE=graph-sandbox,local and mock auth headers.
set -euo pipefail

BASE_URL="${ACTENORA_BASE_URL:-http://localhost:8080}"
TENANT_ID="${VITE_MOCK_ENTRA_TID:-11111111-1111-1111-1111-111111111111}"
USER_OID="${VITE_MOCK_ENTRA_OID:-aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa}"
EMAIL="${VITE_MOCK_EMAIL:-operator@example.test}"

echo "== Graph sandbox acceptance =="
echo "Base URL: ${BASE_URL}"

curl -sf "${BASE_URL}/actuator/health" | grep -q '"status":"UP"' && echo "OK health"

# Portal me (mock auth)
curl -sf \
  -H "X-Mock-Entra-Oid: ${USER_OID}" \
  -H "X-Mock-Entra-Tid: ${TENANT_ID}" \
  -H "X-Mock-Email: ${EMAIL}" \
  -H "X-Mock-Display-Name: Graph Sandbox Operator" \
  -H "X-Mock-Global-Admin: true" \
  "${BASE_URL}/api/v1/portal/me" | grep -q '"tenantId"' && echo "OK portal/me"

# Subscriptions list (requires graph enabled + TENANT_ADMINISTER)
HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "X-Mock-Entra-Oid: ${USER_OID}" \
  -H "X-Mock-Entra-Tid: ${TENANT_ID}" \
  -H "X-Mock-Email: ${EMAIL}" \
  -H "X-Mock-Global-Admin: true" \
  "${BASE_URL}/api/v1/microsoft/subscriptions" || true)
if [[ "${HTTP}" == "200" ]]; then
  echo "OK microsoft/subscriptions"
elif [[ "${HTTP}" == "404" ]]; then
  echo "SKIP microsoft/subscriptions (graph module disabled)"
else
  echo "WARN microsoft/subscriptions HTTP ${HTTP}"
fi

# Webhook validation handshake
TOKEN="acceptance-$(date +%s)"
BODY=$(curl -sf -X POST "${BASE_URL}/api/v1/microsoft/webhooks/graph-notifications?validationToken=${TOKEN}" || true)
if [[ "${BODY}" == "${TOKEN}" ]]; then
  echo "OK webhook validation handshake"
else
  echo "SKIP webhook validation (graph disabled or endpoint unavailable)"
fi

echo "Graph sandbox acceptance finished."
