#!/usr/bin/env bash
# Shared helpers for Actenora monorepo scripts.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export REPO_ROOT

TOOLS_DIR="${REPO_ROOT}/.tools"
ARTIFACTS_DIR="${REPO_ROOT}/artifacts/local"
mkdir -p "${ARTIFACTS_DIR}"

log() { printf '[actenora] %s\n' "$*"; }
warn() { printf '[actenora] WARN: %s\n' "$*" >&2; }
die() { printf '[actenora] ERROR: %s\n' "$*" >&2; exit 1; }

elapsed_ms() {
  local start="$1"
  local end
  end="$(date +%s)"
  echo $(( (end - start) * 1000 ))
}

ensure_path_tools() {
  if [[ -d "${TOOLS_DIR}" ]]; then
    local jdk_home node_bin
    jdk_home="$(find "${TOOLS_DIR}" -maxdepth 3 -type d -path '*/Contents/Home' 2>/dev/null | head -1 || true)"
    if [[ -z "${jdk_home}" ]]; then
      jdk_home="$(find "${TOOLS_DIR}" -maxdepth 2 -type d -name 'jdk-*' 2>/dev/null | head -1 || true)"
      if [[ -n "${jdk_home}" && -d "${jdk_home}/bin" ]]; then
        :
      elif [[ -n "${jdk_home}" && -d "${jdk_home}/Contents/Home" ]]; then
        jdk_home="${jdk_home}/Contents/Home"
      fi
    fi
    if [[ -n "${jdk_home:-}" && -x "${jdk_home}/bin/java" ]]; then
      export JAVA_HOME="${jdk_home}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
    fi
    node_bin="$(find "${TOOLS_DIR}" -maxdepth 2 -type d -name 'node-v*' 2>/dev/null | head -1 || true)"
    if [[ -n "${node_bin}" && -d "${node_bin}/bin" ]]; then
      export PATH="${node_bin}/bin:${PATH}"
    fi
    if [[ -d "${TOOLS_DIR}/maven/bin" ]]; then
      export PATH="${TOOLS_DIR}/maven/bin:${PATH}"
    fi
  fi
  export PATH="${HOME}/.local/bin:${PATH}"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1 (run ./scripts/bootstrap)"
}

java_ok() {
  ensure_path_tools
  command -v java >/dev/null 2>&1 && java -version >/dev/null 2>&1
}

node_ok() {
  ensure_path_tools
  command -v node >/dev/null 2>&1 && command -v pnpm >/dev/null 2>&1
}

python_ok() {
  ensure_path_tools
  command -v uv >/dev/null 2>&1
}

docker_ok() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1
}

mvnw() {
  ensure_path_tools
  (cd "${REPO_ROOT}" && ./mvnw "$@")
}

timing_file() {
  echo "${ARTIFACTS_DIR}/timings.tsv"
}

record_timing() {
  local name="$1"
  local ms="$2"
  local status="${3:-ok}"
  printf '%s\t%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${name}" "${ms}" "${status}" >>"$(timing_file)"
}

run_timed() {
  local name="$1"
  shift
  local start status=ok
  start="$(date +%s)"
  log "START ${name}"
  if "$@"; then
    :
  else
    status=fail
    record_timing "${name}" "$(elapsed_ms "${start}")" "${status}"
    die "${name} failed"
  fi
  local ms
  ms="$(elapsed_ms "${start}")"
  record_timing "${name}" "${ms}" "${status}"
  log "DONE  ${name} (${ms} ms)"
}
