#!/usr/bin/env bash
# Lock Actenora LLM port :8010 to nanobase-meeting-8b only.
# Masks legacy Qwen units and retargets DB-GPT so it cannot revive 35B/27B/MTP.
#
# Meeting pipeline budgets (modules/.../MeetingLlmBudgets.java) assume:
#   llama-server --ctx-size 16384 --parallel 1
# Do not raise ctx-size for meeting notes on CPU; YaRN / 128k is unnecessary and slow.
set -euo pipefail

LEGACY_UNITS=(
  nanobase-qwen36-35b-a3b-mtp.service
  nanobase-qwen36-27b.service
  nanobase-qwen36-mtp.service
)

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    echo "Run as root on nanobase host (or: sudo $0)" >&2
    exit 1
  fi
}

mask_legacy() {
  local unit
  for unit in "${LEGACY_UNITS[@]}"; do
    systemctl disable --now "${unit}" 2>/dev/null || true
    if [[ -f "/etc/systemd/system/${unit}" && ! -L "/etc/systemd/system/${unit}" ]]; then
      mv "/etc/systemd/system/${unit}" \
        "/etc/systemd/system/${unit}.disabled.$(date +%Y%m%d%H%M%S)"
    fi
    rm -f "/etc/systemd/system/multi-user.target.wants/${unit}"
    systemctl mask "${unit}"
    echo "masked ${unit}"
  done
}

retarget_dbgpt() {
  local unit=/etc/systemd/system/nanobase-dbgpt.service
  [[ -f "${unit}" ]] || return 0
  cp "${unit}" "${unit}.bak.$(date +%Y%m%d%H%M%S)"
  if grep -q 'nanobase-qwen36-35b-a3b-mtp.service' "${unit}"; then
    sed -i \
      -e 's/nanobase-qwen36-35b-a3b-mtp\.service/nanobase-qwen3-8b.service/g' \
      -e 's/nanobase-qwen36-mtp\.service/nanobase-qwen3-8b.service/g' \
      -e 's/nanobase-qwen36-27b\.service/nanobase-qwen3-8b.service/g' \
      "${unit}"
  fi
  if ! grep -q '^Environment=LLM_MODEL_NAME=nanobase-meeting-8b$' "${unit}"; then
    awk '
      BEGIN { inserted=0 }
      /^ExecStart=/ && !inserted {
        print "Environment=LLM_MODEL_NAME=nanobase-meeting-8b"
        inserted=1
      }
      { print }
    ' "${unit}" > "${unit}.new"
    mv "${unit}.new" "${unit}"
  fi

  local toml=/data/nanobaseai/bi/frontend/backend/configs/dbgpt-openai-compat.toml
  if [[ -f "${toml}" ]]; then
    cp "${toml}" "${toml}.bak.$(date +%Y%m%d%H%M%S)"
    sed -i \
      -e 's/nanobase-qwen36-35b-a3b-mtp/nanobase-meeting-8b/g' \
      -e 's/nanobase-qwen36-mtp/nanobase-meeting-8b/g' \
      "${toml}"
  fi

  local envf=/data/nanobaseai/bi/frontend/backend/.env
  if [[ -f "${envf}" ]]; then
    if grep -q '^LLM_MODEL_NAME=' "${envf}"; then
      sed -i 's/^LLM_MODEL_NAME=.*/LLM_MODEL_NAME=nanobase-meeting-8b/' "${envf}"
    else
      echo 'LLM_MODEL_NAME=nanobase-meeting-8b' >> "${envf}"
    fi
  fi
}

lock_meeting_8b_ctx() {
  local unit=/etc/systemd/system/nanobase-qwen3-8b.service
  [[ -f "${unit}" ]] || return 0
  if grep -Eq -- '--ctx-size[= ]16384\b' "${unit}"; then
    echo "ctx-size already 16384 on ${unit}"
    return 0
  fi
  cp "${unit}" "${unit}.bak.$(date +%Y%m%d%H%M%S)"
  if grep -Eq -- '--ctx-size[= ][0-9]+' "${unit}"; then
    sed -i -E 's/--ctx-size[= ][0-9]+/--ctx-size 16384/g' "${unit}"
  else
    # Insert after --model / first ExecStart llama-server args
    sed -i -E 's|(ExecStart=.*llama-server)|\1 --ctx-size 16384|' "${unit}"
  fi
  echo "set --ctx-size 16384 on ${unit}"
}

main() {
  require_root
  mask_legacy
  retarget_dbgpt
  lock_meeting_8b_ctx
  systemctl enable nanobase-qwen3-8b.service
  systemctl daemon-reload
  systemctl try-restart nanobase-qwen3-8b.service || true
  systemctl try-restart nanobase-dbgpt.service || true

  echo
  echo "Active LLM:"
  systemctl is-active nanobase-qwen3-8b.service
  curl -sf http://127.0.0.1:8010/v1/models | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"][0]["id"])'
  echo "Legacy start must fail (masked):"
  systemctl start nanobase-qwen36-mtp.service 2>&1 | head -1 || true
}

main "$@"
