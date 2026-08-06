#!/usr/bin/env bash
# Verify (and optionally prune) the host eval-export volume before enabling export.
# Sensitive VTT / quality packs must not be world-readable.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"

ROOT="${ACTENORA_AI_EVAL_EXPORT_HOST_ROOT:-${REPO_ROOT}/artifacts/eval/runtime}"
RETENTION_DAYS="${ACTENORA_AI_EVAL_EXPORT_RETENTION_DAYS:-30}"
PRUNE="${ACTENORA_AI_EVAL_EXPORT_PRUNE:-false}"
ENABLED="${ACTENORA_AI_EVAL_EXPORT_ENABLED:-false}"

mkdir -p "${ROOT}"
# Compose runtime user is uid 999 (actenora). Keep owner-only group/other bits off.
if [[ "$(id -u)" == "0" ]] || command -v sudo >/dev/null 2>&1; then
  sudo chown "${ACTENORA_AI_EVAL_EXPORT_DIR_UID:-999}:${ACTENORA_AI_EVAL_EXPORT_DIR_GID:-999}" "${ROOT}" 2>/dev/null \
    || chown "${ACTENORA_AI_EVAL_EXPORT_DIR_UID:-999}:${ACTENORA_AI_EVAL_EXPORT_DIR_GID:-999}" "${ROOT}" 2>/dev/null \
    || true
fi
chmod 750 "${ROOT}" 2>/dev/null || true

mode="$(stat -f '%Lp' "${ROOT}" 2>/dev/null || stat -c '%a' "${ROOT}")"
# Strip leading zeros for numeric compare (0750 -> 750).
mode_num=$((10#${mode}))
world=$(( mode_num % 10 ))
if (( world != 0 )); then
  die "eval-export root is world-accessible (mode=${mode}): ${ROOT}"
fi

group=$(( (mode_num / 10) % 10 ))
if (( group > 5 )); then
  die "eval-export root group-writable beyond rw-x (mode=${mode}): ${ROOT}"
fi

log "eval-export ACL ok root=${ROOT} mode=${mode} enabled=${ENABLED} retentionDays=${RETENTION_DAYS}"

if [[ "${PRUNE}" == "true" ]]; then
  if ! [[ "${RETENTION_DAYS}" =~ ^[0-9]+$ ]] || (( RETENTION_DAYS < 1 )); then
    die "ACTENORA_AI_EVAL_EXPORT_RETENTION_DAYS must be a positive integer"
  fi
  # Prune files older than retention; keep directory tree.
  find "${ROOT}" -type f -mtime "+${RETENTION_DAYS}" -print -delete | while read -r path; do
    log "pruned ${path}"
  done
  find "${ROOT}" -type d -empty -not -path "${ROOT}" -delete 2>/dev/null || true
  log "eval-export prune complete retentionDays=${RETENTION_DAYS}"
fi

if [[ "${ENABLED}" == "true" ]]; then
  log "eval-export is ENABLED — confirm host volume is access-controlled and retention prune is scheduled"
else
  log "eval-export remains OFF (ACTENORA_AI_EVAL_EXPORT_ENABLED=false) — ACL/retention verified for opt-in"
fi
