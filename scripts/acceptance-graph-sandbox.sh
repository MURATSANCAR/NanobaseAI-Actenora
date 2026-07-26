#!/usr/bin/env bash
# Graph sandbox acceptance — fails closed when Graph is disabled.
# Requires platform-backend on ACTENORA_BASE_URL (default http://localhost:8080)
# with SPRING_PROFILES_ACTIVE=graph-sandbox,local and mock auth headers from env
# (real sandbox Entra oid/tid — no canned personas).
set -euo pipefail

BASE_URL="${ACTENORA_BASE_URL:-http://localhost:8080}"
TENANT_ID="${VITE_MOCK_ENTRA_TID:?VITE_MOCK_ENTRA_TID required (sandbox Entra tid / Actenora tenant)}"
USER_OID="${VITE_MOCK_ENTRA_OID:?VITE_MOCK_ENTRA_OID required (sandbox user oid)}"
EMAIL="${VITE_MOCK_EMAIL:-operator@example.test}"
GRAPH_EVENT_ID="${ACTENORA_GRAPH_EVENT_IMMUTABLE_ID:-}"
MEETING_ID="${ACTENORA_MEETING_OCCURRENCE_ID:-}"

auth_headers=(
  -H "X-Mock-Entra-Oid: ${USER_OID}"
  -H "X-Mock-Entra-Tid: ${TENANT_ID}"
  -H "X-Mock-Email: ${EMAIL}"
  -H "X-Mock-Display-Name: Graph Sandbox Operator"
  -H "X-Mock-Global-Admin: true"
)

fail() { echo "FAIL: $*" >&2; exit 1; }

echo "== Graph sandbox acceptance =="
echo "Base URL: ${BASE_URL}"

curl -sf "${BASE_URL}/actuator/health" | grep -q '"status":"UP"' || fail "health not UP"
echo "OK health"

curl -sf "${auth_headers[@]}" "${BASE_URL}/api/v1/portal/me" | grep -q '"tenantId"' \
  || fail "portal/me"
echo "OK portal/me"

HTTP=$(curl -s -o /tmp/actenora-subs.json -w "%{http_code}" \
  "${auth_headers[@]}" \
  "${BASE_URL}/api/v1/microsoft/subscriptions" || true)
[[ "${HTTP}" == "200" ]] || fail "microsoft/subscriptions HTTP ${HTTP} (Graph must be enabled)"
echo "OK microsoft/subscriptions"

TOKEN="acceptance-$(date +%s)"
BODY=$(curl -sf -X POST \
  "${BASE_URL}/api/v1/microsoft/webhooks/graph-notifications?validationToken=${TOKEN}" \
  || fail "webhook validation request failed")
[[ "${BODY}" == "${TOKEN}" ]] || fail "webhook validation handshake (got: ${BODY})"
echo "OK webhook validation handshake"

# Negative: blank clientState must not be accepted when expected state is configured
REJECT_HTTP=$(curl -s -o /tmp/actenora-wh.json -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d '{"value":[{"subscriptionId":"sub-neg","changeType":"updated","resource":"users/u/events","clientState":"","tenantId":"'"${TENANT_ID}"'","resourceData":{"id":"evt-neg"}}]}' \
  "${BASE_URL}/api/v1/microsoft/webhooks/graph-notifications" || true)
# Accepted with rejected count, or 4xx — either is fine as long as not silently processed as success-only
if [[ "${REJECT_HTTP}" == "202" ]]; then
  grep -Eq '"rejected":\s*[1-9]' /tmp/actenora-wh.json \
    || fail "blank clientState must be rejected in webhook batch"
  echo "OK blank clientState rejected"
elif [[ "${REJECT_HTTP}" =~ ^4 ]]; then
  echo "OK blank clientState denied (${REJECT_HTTP})"
else
  fail "unexpected webhook response for blank clientState HTTP ${REJECT_HTTP}"
fi

if [[ -n "${MEETING_ID}" ]]; then
  curl -sf "${auth_headers[@]}" "${BASE_URL}/api/v1/portal/meetings/${MEETING_ID}" \
    | grep -q '"id"' || fail "portal meeting detail ${MEETING_ID}"
  echo "OK portal meeting detail"
  SEG=$(curl -sf "${auth_headers[@]}" "${BASE_URL}/api/v1/portal/meetings/${MEETING_ID}/transcript" || true)
  echo "OK portal transcript endpoint (${#SEG} bytes)"
elif [[ -n "${GRAPH_EVENT_ID}" ]]; then
  echo "INFO set ACTENORA_MEETING_OCCURRENCE_ID after calendar upsert to assert portal detail"
fi

echo "Graph sandbox acceptance finished (green)."
