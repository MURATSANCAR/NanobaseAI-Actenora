#!/bin/sh
# Import Actenora topology after RabbitMQ has seeded RABBITMQ_DEFAULT_* user.
# management.load_definitions is intentionally unused: it skips default-user seeding.
set -eu

user="${RABBITMQ_USER:-actenora}"
pass="${RABBITMQ_PASSWORD:?RABBITMQ_PASSWORD required}"
host="${RABBITMQ_HOST:-rabbitmq}"
port="${RABBITMQ_MANAGEMENT_PORT:-15672}"
defs="${DEFINITIONS_FILE:-/definitions.json}"

echo "actenora-rabbitmq-init: waiting for management API on ${host}:${port}"
i=0
while [ "${i}" -lt 60 ]; do
  if curl -sf -u "${user}:${pass}" "http://${host}:${port}/api/overview" >/dev/null; then
    break
  fi
  i=$((i + 1))
  sleep 2
done

curl -sf -u "${user}:${pass}" "http://${host}:${port}/api/overview" >/dev/null

echo "actenora-rabbitmq-init: importing ${defs}"
curl -sf -u "${user}:${pass}" \
  -H 'content-type: application/json' \
  -X POST \
  --data-binary @"${defs}" \
  "http://${host}:${port}/api/definitions" >/dev/null

i=0
while [ "${i}" -lt 30 ]; do
  if curl -sf -u "${user}:${pass}" "http://${host}:${port}/api/exchanges/%2F/actenora.domain" >/dev/null; then
    echo "actenora-rabbitmq-init: actenora.domain present"
    exit 0
  fi
  i=$((i + 1))
  sleep 1
done

echo "actenora-rabbitmq-init: ERROR actenora.domain missing after import" >&2
exit 1
