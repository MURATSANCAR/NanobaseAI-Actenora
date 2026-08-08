#!/usr/bin/env bash
# Build the EasyMeeting (Actenora) CUSTOMER Docker bundle.
#
# Produces ONE shippable artifact so the customer never builds from source:
#   dist/actenora-bundle-<tag>.tar.gz
#     ├── images/actenora-images-<tag>.tar   (docker save of all app + base images)
#     ├── compose/docker-compose.customer.yml
#     ├── compose/infrastructure/{postgres,rabbitmq,redis,minio,nginx}/…  (mounted configs)
#     ├── actenora.env.example                (customer fills this in)
#     ├── install.sh                          (docker load + up)
#     └── README-INSTALL.md
#
# The 4 app images are built here from source; the pinned third-party images
# (postgres/redis/rabbitmq/minio/…) are pulled and bundled too, so the customer
# host can be fully air-gapped.
#
# Usage:
#   scripts/package/build-bundle.sh --tag v1.0.0 --env-file /path/to/customer.env
#   scripts/package/build-bundle.sh --tag v1.0.0 --registry ghcr.io/nanobaseai/ --push
#
# Options:
#   --tag TAG           image tag (default: git short sha, else "latest")
#   --env-file FILE     env file to read VITE_* from and bake into the web-portal
#                       image (the SPA config is baked at build time). Recommended.
#   --registry REG      registry prefix (trailing slash), e.g. ghcr.io/nanobaseai/
#   --push              tag + push app images to --registry instead of saving them
#   --skip-thirdparty   don't bundle base images (smaller tar; customer pulls them)
#   --output DIR        output dir (default: <repo>/dist)
#   -h|--help
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"
# common.sh sets -euo pipefail. We manage failures explicitly (die), and disable
# nounset so empty-array expansions are safe on macOS bash 3.2 build hosts.
set +e +u

COMPOSE_DIR="${REPO_ROOT}/infrastructure/compose"
CUSTOMER_COMPOSE="${COMPOSE_DIR}/docker-compose.customer.yml"

TAG=""
ENV_FILE=""
REGISTRY=""
PUSH=0
SKIP_THIRDPARTY=0
OUTPUT_DIR="${REPO_ROOT}/dist"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag) TAG="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --registry) REGISTRY="$2"; shift 2 ;;
    --push) PUSH=1; shift ;;
    --skip-thirdparty) SKIP_THIRDPARTY=1; shift ;;
    --output) OUTPUT_DIR="$2"; shift 2 ;;
    -h|--help) grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "Unknown argument: $1 (see --help)" ;;
  esac
done

docker_ok || die "Docker daemon not reachable. Start Docker and retry."
[[ -f "${CUSTOMER_COMPOSE}" ]] || die "Missing ${CUSTOMER_COMPOSE}"

if [[ -z "${TAG}" ]]; then
  TAG="$(git -C "${REPO_ROOT}" rev-parse --short HEAD 2>/dev/null || echo latest)"
fi
if [[ -n "${PUSH}" && "${PUSH}" -eq 1 && -z "${REGISTRY}" ]]; then
  die "--push requires --registry"
fi

log "Bundle tag: ${TAG}"
[[ -n "${REGISTRY}" ]] && log "Registry prefix: ${REGISTRY}"

# App images built from source: name -> Dockerfile (context is always REPO_ROOT).
declare -a APP_NAMES=(
  "actenora-platform-backend"
  "actenora-web-portal"
  "actenora-teams-meeting-app"
  "actenora-ai-orchestrator"
)
declare -A APP_DOCKERFILE=(
  ["actenora-platform-backend"]="apps/platform-backend/Dockerfile"
  ["actenora-web-portal"]="apps/web-portal/Dockerfile"
  ["actenora-teams-meeting-app"]="apps/teams-meeting-app/Dockerfile"
  ["actenora-ai-orchestrator"]="apps/ai-orchestrator/Dockerfile"
)

# --- collect VITE_* build args for the web-portal (baked at build time) -------
declare -a PORTAL_BUILD_ARGS=()
if [[ -n "${ENV_FILE}" ]]; then
  [[ -f "${ENV_FILE}" ]] || die "env-file not found: ${ENV_FILE}"
  while IFS= read -r _line || [[ -n "${_line}" ]]; do
    _line="${_line%$'\r'}"
    [[ -z "${_line}" || "${_line}" =~ ^[[:space:]]*# ]] && continue
    [[ "${_line}" != VITE_*=* ]] && continue
    PORTAL_BUILD_ARGS+=(--build-arg "${_line}")
  done < "${ENV_FILE}"
  log "web-portal: baking ${#PORTAL_BUILD_ARGS[@]} VITE_* build arg(s) from ${ENV_FILE}"
else
  warn "No --env-file: web-portal image will bake DEFAULT VITE_* (localhost/headers)."
  warn "For a real customer, pass --env-file so the SPA points at their backend/Entra."
fi

# --- build app images ---------------------------------------------------------
for name in "${APP_NAMES[@]}"; do
  dockerfile="${APP_DOCKERFILE[$name]}"
  local_ref="${name}:${TAG}"
  log "Building ${local_ref} (${dockerfile})…"
  extra=()
  [[ "${name}" == "actenora-web-portal" ]] && extra=("${PORTAL_BUILD_ARGS[@]}")
  if ! docker build -f "${REPO_ROOT}/${dockerfile}" -t "${local_ref}" "${extra[@]}" "${REPO_ROOT}"; then
    die "docker build failed for ${name}"
  fi
done

# --- third-party (pinned) images referenced by the compose --------------------
# Pull every literal image ref (i.e. not the templated app images).
mapfile -t THIRDPARTY < <(
  grep -E '^\s*image:' "${CUSTOMER_COMPOSE}" \
    | sed -E 's/^\s*image:\s*//' \
    | grep -v '\${ACTENORA_IMAGE_REGISTRY' \
    | grep -v '\${VLLM_IMAGE' \
    | sort -u
)
if [[ "${SKIP_THIRDPARTY}" -eq 0 ]]; then
  for img in "${THIRDPARTY[@]}"; do
    log "Pulling base image ${img}…"
    docker pull "${img}" || die "docker pull failed for ${img}"
  done
fi

# --- push OR save -------------------------------------------------------------
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/actenora-bundle.XXXXXX")"
trap 'rm -rf "${STAGE}"' EXIT
mkdir -p "${STAGE}/images" "${STAGE}/compose"

declare -a SAVE_REFS=()
for name in "${APP_NAMES[@]}"; do
  local_ref="${name}:${TAG}"
  if [[ "${PUSH}" -eq 1 ]]; then
    remote_ref="${REGISTRY}${name}:${TAG}"
    log "Tagging + pushing ${remote_ref}…"
    docker tag "${local_ref}" "${remote_ref}" || die "docker tag failed: ${remote_ref}"
    docker push "${remote_ref}" || die "docker push failed: ${remote_ref}"
  else
    SAVE_REFS+=("${local_ref}")
  fi
done

if [[ "${PUSH}" -eq 1 ]]; then
  log "Images pushed to ${REGISTRY}. (No offline image tar produced.)"
else
  [[ "${SKIP_THIRDPARTY}" -eq 0 ]] && SAVE_REFS+=("${THIRDPARTY[@]}")
  IMAGES_TAR="${STAGE}/images/actenora-images-${TAG}.tar"
  log "Saving ${#SAVE_REFS[@]} image(s) → $(basename "${IMAGES_TAR}")…"
  docker save "${SAVE_REFS[@]}" -o "${IMAGES_TAR}" || die "docker save failed"
  log "Image tar: $(du -h "${IMAGES_TAR}" | cut -f1)"
fi

# --- stage compose + mounted config trees -------------------------------------
cp "${CUSTOMER_COMPOSE}" "${STAGE}/compose/docker-compose.customer.yml"
mkdir -p "${STAGE}/compose/infrastructure"
for d in postgres rabbitmq redis minio nginx; do
  if [[ -d "${REPO_ROOT}/infrastructure/${d}" ]]; then
    cp -R "${REPO_ROOT}/infrastructure/${d}" "${STAGE}/compose/infrastructure/${d}"
  fi
done
# The compose uses ../postgres etc. relative to the compose dir; keep that layout:
#   compose/docker-compose.customer.yml  +  compose/infrastructure/<svc>
# The install script rewrites the mount base to ./infrastructure via COMPOSE_DIR.

# --- stage env template + install assets --------------------------------------
if [[ -f "${SCRIPT_DIR}/templates/actenora.env.example" ]]; then
  cp "${SCRIPT_DIR}/templates/actenora.env.example" "${STAGE}/actenora.env.example"
elif [[ -f "${COMPOSE_DIR}/.env.customer.example" ]]; then
  cp "${COMPOSE_DIR}/.env.customer.example" "${STAGE}/actenora.env.example"
else
  cp "${COMPOSE_DIR}/.env.prod.example" "${STAGE}/actenora.env.example"
fi
# Record the tag the bundle was built with so install.sh defaults match.
printf 'ACTENORA_IMAGE_TAG=%s\n' "${TAG}" >> "${STAGE}/actenora.env.example"

for f in install.sh README-INSTALL.md; do
  [[ -f "${SCRIPT_DIR}/templates/${f}" ]] && cp "${SCRIPT_DIR}/templates/${f}" "${STAGE}/${f}"
done
[[ -f "${STAGE}/install.sh" ]] && chmod +x "${STAGE}/install.sh"

# provenance stamp
{
  echo "bundle_tag=${TAG}"
  echo "git_commit=$(git -C "${REPO_ROOT}" rev-parse HEAD 2>/dev/null || echo unknown)"
  echo "built_by=$(whoami 2>/dev/null || echo unknown)"
  echo "registry=${REGISTRY:-<offline>}"
  echo "thirdparty_bundled=$([[ "${SKIP_THIRDPARTY}" -eq 0 && "${PUSH}" -eq 0 ]] && echo yes || echo no)"
} > "${STAGE}/BUNDLE-INFO.txt"

# --- final tarball ------------------------------------------------------------
mkdir -p "${OUTPUT_DIR}"
OUT_TAR="${OUTPUT_DIR}/actenora-bundle-${TAG}.tar.gz"
log "Packing bundle → ${OUT_TAR}…"
tar -C "${STAGE}" -czf "${OUT_TAR}" . || die "failed to pack bundle"

log "DONE."
log "  Bundle:  ${OUT_TAR}  ($(du -h "${OUT_TAR}" | cut -f1))"
if [[ "${PUSH}" -eq 1 ]]; then
  log "  Images:  pushed to ${REGISTRY} (tag ${TAG})"
  log "  Customer: set ACTENORA_IMAGE_REGISTRY=${REGISTRY} + ACTENORA_IMAGE_TAG=${TAG}, then 'docker compose pull && up -d'"
else
  log "  Images:  bundled offline (docker load happens in install.sh)"
  log "  Ship the .tar.gz; customer runs ./install.sh after filling actenora.env"
fi
