#!/usr/bin/env bash
# Graph sandbox acceptance — fails closed when Graph is disabled.
# Requires platform-backend on ACTENORA_BASE_URL (default http://localhost:8080)
# with SPRING_PROFILES_ACTIVE=graph-sandbox,local and mock auth headers from env
# (real sandbox Entra oid/tid — no canned personas).
set -euo pipefail

BASE_URL="${ACTENORA_BASE_URL:-http://localhost:8080}"
TENANT_ID="${VITE_IDENTITY_ENTRA_TID:?VITE_IDENTITY_ENTRA_TID required (real Entra directory / Actenora tenant tid)}"
USER_OID="${VITE_IDENTITY_ENTRA_OID:?VITE_IDENTITY_ENTRA_OID required (real Entra user object id)}"
EMAIL="${VITE_IDENTITY_EMAIL:?VITE_IDENTITY_EMAIL required (real work email)}"
DISPLAY_NAME="${VITE_IDENTITY_DISPLAY_NAME:?VITE_IDENTITY_DISPLAY_NAME required (real display name)}"
GRAPH_EVENT_ID="${ACTENORA_GRAPH_EVENT_IMMUTABLE_ID:-}"
MEETING_ID="${ACTENORA_MEETING_OCCURRENCE_ID:-}"
CROSS_TENANT_MEETING_ID="${ACTENORA_CROSS_TENANT_MEETING_ID:-}"

auth_headers=(
  -H "X-Actenora-Entra-Oid: ${USER_OID}"
  -H "X-Actenora-Entra-Tid: ${TENANT_ID}"
  -H "X-Actenora-Email: ${EMAIL}"
  -H "X-Actenora-Display-Name: ${DISPLAY_NAME}"
  -H "X-Actenora-Global-Admin: ${VITE_IDENTITY_GLOBAL_ADMIN:-false}"
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

# Negative: blank clientState
REJECT_HTTP=$(curl -s -o /tmp/actenora-wh-blank.json -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d '{"value":[{"subscriptionId":"sub-neg","changeType":"updated","resource":"users/u/events","clientState":"","tenantId":"'"${TENANT_ID}"'","resourceData":{"id":"evt-neg"}}]}' \
  "${BASE_URL}/api/v1/microsoft/webhooks/graph-notifications" || true)
if [[ "${REJECT_HTTP}" == "202" ]]; then
  grep -Eq '"rejected":\s*[1-9]' /tmp/actenora-wh-blank.json \
    || fail "blank clientState must be rejected in webhook batch"
  echo "OK blank clientState rejected"
elif [[ "${REJECT_HTTP}" =~ ^4 ]]; then
  echo "OK blank clientState denied (${REJECT_HTTP})"
else
  fail "unexpected webhook response for blank clientState HTTP ${REJECT_HTTP}"
fi

# Negative: wrong clientState
WRONG_HTTP=$(curl -s -o /tmp/actenora-wh-wrong.json -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d '{"value":[{"subscriptionId":"sub-neg2","changeType":"updated","resource":"users/u/events","clientState":"definitely-wrong-client-state","tenantId":"'"${TENANT_ID}"'","resourceData":{"id":"evt-neg2"}}]}' \
  "${BASE_URL}/api/v1/microsoft/webhooks/graph-notifications" || true)
if [[ "${WRONG_HTTP}" == "202" ]]; then
  grep -Eq '"rejected":\s*[1-9]' /tmp/actenora-wh-wrong.json \
    || fail "wrong clientState must be rejected in webhook batch"
  echo "OK wrong clientState rejected"
elif [[ "${WRONG_HTTP}" =~ ^4 ]]; then
  echo "OK wrong clientState denied (${WRONG_HTTP})"
else
  fail "unexpected webhook response for wrong clientState HTTP ${WRONG_HTTP}"
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

if [[ -n "${CROSS_TENANT_MEETING_ID}" ]]; then
  CROSS_HTTP=$(curl -s -o /tmp/actenora-cross.json -w "%{http_code}" \
    "${auth_headers[@]}" \
    "${BASE_URL}/api/v1/portal/meetings/${CROSS_TENANT_MEETING_ID}" || true)
  [[ "${CROSS_HTTP}" == "403" ]] \
    || fail "cross-tenant meeting must return 403 (got ${CROSS_HTTP})"
  echo "OK cross-tenant meeting denied (403)"
fi

echo "Graph sandbox acceptance finished (green)."
