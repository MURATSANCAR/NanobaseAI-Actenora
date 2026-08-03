#!/usr/bin/env bash
# Build platform-backend and run Actenora data plane + BFF on portal.nanobase.ai host.
# Nginx must route /api/v1/portal/ → 127.0.0.1:8088 (see scripts/server/nginx/actenora-portal-api.location.conf).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
ensure_path_tools

SSH_HOST="${ACTENORA_BACKEND_SSH_HOST:-${ACTENORA_PORTAL_SSH_HOST:-nanobase}}"
REMOTE_ROOT="${ACTENORA_BACKEND_REMOTE_ROOT:-/data/nanobaseai/actenora}"
REMOTE_ENV="${ACTENORA_BACKEND_ENV_FILE:-/etc/nanobaseai/actenora.env}"
BACKEND_HOST_PORT="${ACTENORA_PLATFORM_BACKEND_HOST_PORT:-8088}"
NGINX_SITE="${ACTENORA_PORTAL_NGINX_SITE:-/etc/nginx/sites-enabled/portal.nanobase.ai}"
PUBLIC_API_URL="${ACTENORA_PORTAL_PUBLIC_API_URL:-https://portal.nanobase.ai/api/v1/portal/me}"

require_cmd rsync
require_cmd ssh
require_cmd openssl

COMPOSE_DIR="${REPO_ROOT}/infrastructure/compose"
COMPOSE_BASE="${COMPOSE_DIR}/docker-compose.prod-like.yml"
COMPOSE_OVERRIDE="${COMPOSE_DIR}/docker-compose.portal-server.override.yml"
JAR_GLOB="${REPO_ROOT}/apps/platform-backend/target/platform-backend-*.jar"
RUNTIME_CTX="${ARTIFACTS_DIR}/actenora-backend-runtime"

generate_secret() {
  openssl rand -hex 16
}

ensure_remote_env() {
  if ssh "${SSH_HOST}" "test -f '${REMOTE_ENV}'"; then
    log "Using existing server env: ${REMOTE_ENV}"
    # Keep secrets; upsert long-meeting AI timeout knobs so deploys stay future-proof.
    ssh "${SSH_HOST}" "sudo python3 -" <<'PY'
from pathlib import Path
p = Path("/etc/nanobaseai/actenora.env")
keys = {
    "ACTENORA_AI_PROVIDER_READ_TIMEOUT": "7200s",
    "ACTENORA_AI_WORKER_STALE_RUNNING_AFTER": "PT24H",
    # Single-instance prodlike: requeue RUNNING jobs orphaned by container restart.
    "ACTENORA_AI_WORKER_RECLAIM_ORPHANS_ON_STARTUP": "true",
}
lines = p.read_text().splitlines()
out, seen = [], set()
for line in lines:
    if not line or line.startswith("#") or "=" not in line:
        out.append(line)
        continue
    k = line.split("=", 1)[0]
    if k in keys:
        out.append(f"{k}={keys[k]}")
        seen.add(k)
    else:
        out.append(line)
for k, v in keys.items():
    if k not in seen:
        out.append(f"{k}={v}")
p.write_text("\n".join(out) + "\n")
print("upserted AI timeout knobs in", p)
PY
    return
  fi

  log "Creating ${REMOTE_ENV} from template + repo .env Entra settings"
  local pg rmq minio_access minio_secret graph delivery portal
  pg="actenora_pg_$(generate_secret)"
  rmq="actenora_rmq_$(generate_secret)"
  minio_access="actenora_minio_$(generate_secret)"
  minio_secret="actenora_minio_sk_$(generate_secret)"
  graph="actenora_graph_cs_$(generate_secret)"
  delivery="actenora_delivery_wh_$(generate_secret)"
  portal="actenora_portal_hmac_$(generate_secret)"

  local entra_issuer="" entra_audience="" cors="https://portal.nanobase.ai"
  if [[ -f "${REPO_ROOT}/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "${REPO_ROOT}/.env"
    set +a
    entra_issuer="${ACTENORA_ENTRA_ISSUER_URI:-}"
    entra_audience="${ACTENORA_ENTRA_AUDIENCE:-}"
    if [[ -n "${ACTENORA_CORS_ALLOWED_ORIGINS:-}" ]]; then
      cors="${ACTENORA_CORS_ALLOWED_ORIGINS}"
      if [[ "${cors}" != *"portal.nanobase.ai"* ]]; then
        cors="${cors},https://portal.nanobase.ai"
      fi
    fi
  fi

  ssh "${SSH_HOST}" "sudo mkdir -p /etc/nanobaseai && sudo touch '${REMOTE_ENV}' && sudo chmod 600 '${REMOTE_ENV}'"
  ssh "${SSH_HOST}" "sudo tee '${REMOTE_ENV}' > /dev/null" <<EOF
ACTENORA_ENV=portal-server
SPRING_PROFILES_ACTIVE=prod,prod-fixture,it
POSTGRES_PASSWORD=${pg}
RABBITMQ_PASSWORD=${rmq}
OBJECT_STORAGE_ACCESS_KEY=${minio_access}
OBJECT_STORAGE_SECRET_KEY=${minio_secret}
ACTENORA_MICROSOFT_GRAPH_CLIENT_STATE=${graph}
ACTENORA_DELIVERY_WEBHOOK_SECRET=${delivery}
ACTENORA_PORTAL_LINK_SECRET=${portal}
ACTENORA_AUTH_MODE=entra
ACTENORA_PORTAL_AUTH_MODE=msal
ACTENORA_ENTRA_ISSUER_URI=${entra_issuer}
ACTENORA_ENTRA_AUDIENCE=${entra_audience}
ACTENORA_CORS_ALLOWED_ORIGINS=${cors}
ACTENORA_PLATFORM_BACKEND_HOST_PORT=${BACKEND_HOST_PORT}
PLATFORM_BACKEND_PORT=8080
ACTENORA_PERSISTENCE_MODE=jdbc
ACTENORA_MESSAGING_MODE=jdbc-rabbit
ACTENORA_MICROSOFT_GRAPH_ENABLED=false
ACTENORA_AI_PROVIDER_KIND=nanobaseai
ACTENORA_AI_PROVIDER_BASE_URL=http://host.docker.internal:8010
# Long meetings: single LLM call can exceed 30m; end-to-end pipeline can take many hours.
ACTENORA_AI_PROVIDER_READ_TIMEOUT=7200s
ACTENORA_AI_PROVIDER_MAX_ATTEMPTS=5
ACTENORA_AI_PROVIDER_FAST_EXTRACTION_SERVED_MODEL_ID=nanobase-qwen36-35b-a3b-mtp
ACTENORA_AI_PROVIDER_FINAL_SERVED_MODEL_ID=nanobase-qwen36-35b-a3b-mtp
ACTENORA_AI_PIPELINE_MODE=legacy
ACTENORA_AI_FINALIZATION_MODE=editorial
ACTENORA_AI_FINALIZATION_PROMPT_RESOURCE=/aiprocessing/prompts/editorial-summary.v1.txt
ACTENORA_AI_FINALIZATION_PROMPT_VERSION=pv-meeting-editorial-summary-v1
ACTENORA_AI_FINALIZATION_SCHEMA=meeting.editorial-summary.v1
ACTENORA_AI_FINALIZATION_TASK_TYPE=FINAL_NOTE
ACTENORA_AI_FINALIZATION_MAX_OUTPUT_TOKENS=768
ACTENORA_AI_FINALIZATION_TIMEOUT_SECONDS=1800
ACTENORA_AI_FINALIZATION_FAILURE_MODE=deterministic
ACTENORA_AI_WORKER_ENABLED=true
ACTENORA_AI_WORKER_STALE_RUNNING_AFTER=PT24H
ACTENORA_AI_WORKER_RECLAIM_ORPHANS_ON_STARTUP=true
ACTENORA_ALLOW_DEFAULT_SECRETS=false
EOF
}

install_nginx_snippet() {
  log "Ensuring nginx routes /api/v1/portal/ → 127.0.0.1:${BACKEND_HOST_PORT}"
  rsync -az "${REPO_ROOT}/scripts/server/nginx/actenora-portal-api.upstream.conf" \
    "${SSH_HOST}:/tmp/actenora-portal-api.upstream.conf"
  rsync -az "${REPO_ROOT}/scripts/server/nginx/actenora-portal-api.location.conf" \
    "${SSH_HOST}:/tmp/actenora-portal-api.location.conf"
  rsync -az "${REPO_ROOT}/scripts/server/nginx/actenora-portal-spa.location.conf" \
    "${SSH_HOST}:/tmp/actenora-portal-spa.location.conf"
  rsync -az "${REPO_ROOT}/scripts/server/nginx/actenora-microsoft-api.location.conf" \
    "${SSH_HOST}:/tmp/actenora-microsoft-api.location.conf"
  ssh "${SSH_HOST}" bash -s <<EOF
set -euo pipefail
SITE="${NGINX_SITE}"
UP="/tmp/actenora-portal-api.upstream.conf"
LOC="/tmp/actenora-portal-api.location.conf"
SPA="/tmp/actenora-portal-spa.location.conf"
MS="/tmp/actenora-microsoft-api.location.conf"

need_reload=0
if ! sudo nginx -t >/dev/null 2>&1; then
  LATEST="\$(ls -t /tmp/portal.nanobase.ai.bak.actenora.* 2>/dev/null | head -1 || true)"
  if [[ -n "\${LATEST}" ]]; then
    echo "Restoring broken nginx site from \${LATEST}"
    sudo cp "\${LATEST}" "\$SITE"
  fi
fi

if grep -q 'upstream actenora_platform_backend' "\$SITE" 2>/dev/null \
   && grep -q 'location /api/v1/portal/' "\$SITE" 2>/dev/null \
   && grep -q 'location /api/v1/microsoft/' "\$SITE" 2>/dev/null \
   && grep -q 'location ^~ /easymeeting/' "\$SITE" 2>/dev/null; then
  echo "nginx already configured for Actenora BFF + EasyMeeting SPA"
  sudo nginx -t
  exit 0
fi

sudo cp "\$SITE" "/tmp/portal.nanobase.ai.bak.actenora.\$(date +%Y%m%d%H%M%S)"

if ! grep -q 'upstream actenora_platform_backend' "\$SITE"; then
  sudo awk -v up="\$UP" '
    /^server \{/ && !done {
      while ((getline line < up) > 0) print line
      close(up)
      print ""
      done=1
    }
    { print }
  ' "\$SITE" | sudo tee "\${SITE}.new" > /dev/null
  sudo mv "\${SITE}.new" "\$SITE"
  need_reload=1
fi

if ! grep -q 'location /api/v1/portal/' "\$SITE"; then
  sudo awk -v loc="\$LOC" '
    /location \\/api\\/ \{/ && !done {
      while ((getline line < loc) > 0) print line
      close(loc)
      done=1
    }
    { print }
  ' "\$SITE" | sudo tee "\${SITE}.new" > /dev/null
  sudo mv "\${SITE}.new" "\$SITE"
  need_reload=1
fi

if ! grep -q 'location /api/v1/microsoft/' "\$SITE"; then
  sudo awk -v ms="\$MS" '
    /location \\/api\\/v1\\/portal\\/ \{/ && !done {
      while ((getline line < ms) > 0) print line
      close(ms)
      print ""
      done=1
    }
    { print }
  ' "\$SITE" | sudo tee "\${SITE}.new" > /dev/null
  sudo mv "\${SITE}.new" "\$SITE"
  need_reload=1
fi

if ! grep -q 'location ^~ /easymeeting/' "\$SITE"; then
  sudo awk -v spa="\$SPA" '
    /location = \\/index.html/ && !done {
      while ((getline line < spa) > 0) print line
      close(spa)
      done=1
    }
    { print }
  ' "\$SITE" | sudo tee "\${SITE}.new" > /dev/null
  sudo mv "\${SITE}.new" "\$SITE"
  need_reload=1
fi

sudo nginx -t
if [[ "\${need_reload}" == "1" ]]; then
  sudo systemctl reload nginx
  echo "nginx reloaded with Actenora BFF + SPA routes"
else
  echo "nginx verified (no changes)"
fi
EOF
}

build_backend_jar() {
  log "Building platform-backend JAR"
  mvnw -pl apps/platform-backend -am -Dmaven.test.skip=true package
  local jar
  jar="$(ls -1 ${JAR_GLOB} 2>/dev/null | head -1 || true)"
  [[ -n "${jar}" && -f "${jar}" ]] || die "platform-backend JAR not found under apps/platform-backend/target/"
  log "Built ${jar}"
}

stage_runtime_context() {
  local jar
  jar="$(ls -1 ${JAR_GLOB} | head -1)"
  rm -rf "${RUNTIME_CTX}"
  mkdir -p "${RUNTIME_CTX}/infrastructure/compose"
  cp "${jar}" "${RUNTIME_CTX}/infrastructure/compose/app.jar"
  cp "${COMPOSE_BASE}" "${RUNTIME_CTX}/infrastructure/compose/"
  cp "${COMPOSE_OVERRIDE}" "${RUNTIME_CTX}/infrastructure/compose/"
  cp "${REPO_ROOT}/apps/platform-backend/Dockerfile.runtime" \
    "${RUNTIME_CTX}/infrastructure/compose/Dockerfile.runtime"
  rsync -a \
    "${REPO_ROOT}/infrastructure/postgres/" "${RUNTIME_CTX}/infrastructure/postgres/"
  rsync -a \
    "${REPO_ROOT}/infrastructure/rabbitmq/" "${RUNTIME_CTX}/infrastructure/rabbitmq/"
  rsync -a \
    "${REPO_ROOT}/infrastructure/redis/" "${RUNTIME_CTX}/infrastructure/redis/"
  rsync -a \
    "${REPO_ROOT}/infrastructure/minio/" "${RUNTIME_CTX}/infrastructure/minio/"
}

deploy_compose_stack() {
  log "Uploading runtime bundle to ${SSH_HOST}:${REMOTE_ROOT}"
  ssh "${SSH_HOST}" "sudo mkdir -p '${REMOTE_ROOT}/infrastructure' && sudo chown -R \$(whoami):\$(whoami) '${REMOTE_ROOT}'"
  rsync -az --delete "${RUNTIME_CTX}/infrastructure/" "${SSH_HOST}:${REMOTE_ROOT}/infrastructure/"

  log "Starting Actenora stack (docker compose)"
  ssh "${SSH_HOST}" bash -s <<EOF
set -euo pipefail
cd '${REMOTE_ROOT}/infrastructure/compose'
sudo docker compose \\
  -f docker-compose.prod-like.yml \\
  -f docker-compose.portal-server.override.yml \\
  --env-file '${REMOTE_ENV}' \\
  up -d --build --remove-orphans
EOF
}

wait_for_backend() {
  log "Waiting for backend health on 127.0.0.1:${BACKEND_HOST_PORT}"
  local i code
  for i in $(seq 1 60); do
    code="$(ssh "${SSH_HOST}" "curl -sf -o /dev/null -w '%{http_code}' http://127.0.0.1:${BACKEND_HOST_PORT}/actuator/health/liveness" 2>/dev/null || echo 000)"
    if [[ "${code}" == "200" ]]; then
      log "Backend liveness OK"
      return 0
    fi
    sleep 5
  done
  warn "Backend health check did not pass within 5 minutes — check: ssh ${SSH_HOST} docker logs actenora-prodlike-platform-backend"
  return 1
}

smoke_public_api() {
  log "Smoke test (expect 401 without Bearer — proves routing, not auth)"
  local code body
  code="$(curl -sf -o /dev/null -w '%{http_code}' "${PUBLIC_API_URL}" 2>/dev/null || echo 000)"
  if [[ "${code}" == "401" || "${code}" == "403" ]]; then
    log "Public API routing OK (${code} without token)"
    return 0
  fi
  body="$(curl -s "${PUBLIC_API_URL}" 2>/dev/null | head -c 200 || true)"
  if [[ "${code}" == "200" ]]; then
    log "Public API returned 200 (mock/fixture auth may be active)"
    return 0
  fi
  warn "Unexpected API response: HTTP ${code} — ${body}"
  return 1
}

main() {
  ensure_remote_env
  build_backend_jar
  stage_runtime_context
  install_nginx_snippet
  deploy_compose_stack
  wait_for_backend || true
  smoke_public_api || true
  log "Actenora backend deploy complete"
  log "  BFF upstream: 127.0.0.1:${BACKEND_HOST_PORT}"
  log "  Portal API:   https://portal.nanobase.ai/api/v1/portal/"
  log "  Redeploy SPA with VITE_API_BASE_URL=https://portal.nanobase.ai (make deploy-portal)"
}

main "$@"
