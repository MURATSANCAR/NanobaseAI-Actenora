#!/usr/bin/env bash
# EasyMeeting (Actenora) — customer installer.
# Runs from inside the extracted bundle directory. Loads the shipped images and
# starts the stack. No source build, no JDK/Maven/Node required — just Docker.
#
# Usage:
#   ./install.sh                 # load images + start (default profiles)
#   ./install.sh --profile ai    # also start the ai-orchestrator sidecar
#   ./install.sh --no-load       # skip docker load (images already present / registry pull)
#   ./install.sh --pull          # docker compose pull (registry mode) instead of load
#   ./install.sh down            # stop the stack (keeps data volumes)
#   ./install.sh logs            # follow logs
#   ./install.sh ps              # status
# nounset is intentionally OFF: this wrapper forwards optional array args and must
# run on older bash (e.g. macOS 3.2) where empty-array expansion trips `set -u`.
set -o pipefail

BUNDLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="${BUNDLE_DIR}/compose"
COMPOSE_FILE="${COMPOSE_DIR}/docker-compose.customer.yml"
ENV_FILE="${BUNDLE_DIR}/actenora.env"
IMAGES_DIR="${BUNDLE_DIR}/images"

# Infra config files are shipped under compose/infrastructure/<svc>; point the
# compose bind-mounts there (default in-repo layout is ..).
export ACTENORA_INFRA_DIR="./infrastructure"

red()  { printf '\033[31m%s\033[0m\n' "$*"; }
grn()  { printf '\033[32m%s\033[0m\n' "$*"; }
ylw()  { printf '\033[33m%s\033[0m\n' "$*"; }
die()  { red "ERROR: $*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || die "docker not found. Install Docker Engine + compose plugin."
docker info >/dev/null 2>&1 || die "Docker daemon not reachable. Start Docker and retry."
docker compose version >/dev/null 2>&1 || die "'docker compose' plugin not found."
[[ -f "${COMPOSE_FILE}" ]] || die "Missing ${COMPOSE_FILE} (corrupt bundle?)."

DO_LOAD=1
DO_PULL=0
declare -a PASS=()      # extra args forwarded to compose (e.g. --profile ai)
SUBCMD="up"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-load) DO_LOAD=0; shift ;;
    --pull) DO_PULL=1; DO_LOAD=0; shift ;;
    up|down|logs|ps|restart) SUBCMD="$1"; shift ;;
    --profile) PASS+=(--profile "$2"); shift 2 ;;
    *) PASS+=("$1"); shift ;;
  esac
done

compose() {
  ( cd "${COMPOSE_DIR}" && docker compose -f docker-compose.customer.yml --env-file "${ENV_FILE}" "$@" )
}

case "${SUBCMD}" in
  down)  compose "${PASS[@]}" down; exit $? ;;
  logs)  compose "${PASS[@]}" logs -f --tail=200; exit $? ;;
  ps)    compose "${PASS[@]}" ps; exit $? ;;
esac

# --- 1. env file --------------------------------------------------------------
if [[ ! -f "${ENV_FILE}" ]]; then
  if [[ -f "${BUNDLE_DIR}/actenora.env.example" ]]; then
    cp "${BUNDLE_DIR}/actenora.env.example" "${ENV_FILE}"
    chmod 600 "${ENV_FILE}"
    ylw "Created ${ENV_FILE} from the template."
    red  "Fill in every (REQUIRED) value, then re-run ./install.sh"
    exit 2
  fi
  die "No actenora.env and no template to copy."
fi
if grep -Eq 'change_me|actenora_local' "${ENV_FILE}"; then
  die "actenora.env still contains placeholder secrets (change_me / actenora_local). Replace them."
fi

# --- 2. load images -----------------------------------------------------------
if [[ "${DO_LOAD}" -eq 1 ]]; then
  shopt -s nullglob
  tars=( "${IMAGES_DIR}"/*.tar "${IMAGES_DIR}"/*.tar.gz )
  shopt -u nullglob
  [[ ${#tars[@]} -gt 0 ]] || die "No image tar under ${IMAGES_DIR}. Use --pull for registry mode, or --no-load."
  for t in "${tars[@]}"; do
    grn "Loading images from $(basename "${t}")…"
    docker load -i "${t}" || die "docker load failed for ${t}"
  done
elif [[ "${DO_PULL}" -eq 1 ]]; then
  grn "Pulling images from registry…"
  compose "${PASS[@]}" pull || die "docker compose pull failed"
fi

# --- 3. validate compose interpolation ---------------------------------------
grn "Validating configuration…"
compose "${PASS[@]}" config >/dev/null || die "compose config failed — check required values in actenora.env."

# --- 4. up --------------------------------------------------------------------
grn "Starting the stack…"
compose "${PASS[@]}" up -d || die "docker compose up failed"

echo
compose "${PASS[@]}" ps
echo
grn "EasyMeeting is starting. Backend liveness may take ~2 min on first boot (Flyway migrations)."
BE_PORT="$(grep -E '^ACTENORA_PLATFORM_BACKEND_HOST_PORT=' "${ENV_FILE}" | cut -d= -f2)"; BE_PORT="${BE_PORT:-8088}"
FE_PORT="$(grep -E '^WEB_PORTAL_HOST_PORT=' "${ENV_FILE}" | cut -d= -f2)"; FE_PORT="${FE_PORT:-3000}"
echo   "  Backend health : curl -fsS http://127.0.0.1:${BE_PORT}/actuator/health"
echo   "  Portal         : http://127.0.0.1:${FE_PORT}/  (front with nginx/TLS for real access)"
echo   "  Logs           : ./install.sh logs"
