#!/bin/sh
# RabbitMQ official image skips RABBITMQ_DEFAULT_* when management.load_definitions
# is set. We start the broker without that conf option, ensure the app user from
# env, then import topology definitions.
set -eu

ensure_user_and_topology() {
  rabbitmqctl await_startup

  user="${RABBITMQ_DEFAULT_USER:-actenora}"
  pass="${RABBITMQ_DEFAULT_PASS:?RABBITMQ_DEFAULT_PASS required}"
  vhost="${RABBITMQ_DEFAULT_VHOST:-/}"

  if ! rabbitmqctl authenticate_user "${user}" "${pass}" >/dev/null 2>&1; then
    if rabbitmqctl add_user "${user}" "${pass}" >/dev/null 2>&1; then
      echo "actenora-rabbitmq: created user ${user}"
    else
      rabbitmqctl change_password "${user}" "${pass}" >/dev/null
      echo "actenora-rabbitmq: updated password for ${user}"
    fi
  fi

  rabbitmqctl set_permissions -p "${vhost}" "${user}" ".*" ".*" ".*" >/dev/null
  rabbitmqctl set_user_tags "${user}" administrator >/dev/null 2>&1 || true

  if [ -f /etc/rabbitmq/definitions.json ]; then
    rabbitmqctl import_definitions /etc/rabbitmq/definitions.json
    echo "actenora-rabbitmq: imported definitions.json"
  fi
}

ensure_user_and_topology &
exec docker-entrypoint.sh rabbitmq-server
