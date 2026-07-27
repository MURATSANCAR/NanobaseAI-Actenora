#!/usr/bin/env bash
# Restore Actenora LLM port :8010 to nanobase-qwen36-35b-a3b-mtp and remove 8B.
# Inverse of lock-meeting-8b-llm.sh.
set -euo pipefail

MODEL_ID=nanobase-qwen36-35b-a3b-mtp
UNIT=nanobase-qwen36-35b-a3b-mtp.service
ENVF=/etc/nanobaseai/qwen36-35b-a3b-mtp.env
EIGHT_B_UNIT=nanobase-qwen3-8b.service

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    echo "Run as root on nanobase host (or: sudo $0)" >&2
    exit 1
  fi
}

stop_and_remove_8b() {
  systemctl disable --now "${EIGHT_B_UNIT}" 2>/dev/null || true
  if [[ -f "/etc/systemd/system/${EIGHT_B_UNIT}" && ! -L "/etc/systemd/system/${EIGHT_B_UNIT}" ]]; then
    mv "/etc/systemd/system/${EIGHT_B_UNIT}" \
      "/etc/systemd/system/${EIGHT_B_UNIT}.removed.$(date +%Y%m%d%H%M%S)"
  fi
  rm -f "/etc/systemd/system/multi-user.target.wants/${EIGHT_B_UNIT}"
  if [[ -f /etc/nanobaseai/qwen3-8b.env ]]; then
    mv /etc/nanobaseai/qwen3-8b.env \
      "/etc/nanobaseai/qwen3-8b.env.removed.$(date +%Y%m%d%H%M%S)"
  fi
  # Remove GGUF weights (keep download.log if present for audit).
  local gguf=/opt/nanobaseai/models/qwen3-8b/Qwen3-8B-Q5_K_M.gguf
  if [[ -f "${gguf}" ]]; then
    echo "removing ${gguf}"
    rm -f "${gguf}"
  fi
  echo "removed ${EIGHT_B_UNIT}"
}

restore_35b_env() {
  mkdir -p /etc/nanobaseai
  local bak
  bak="$(ls -1t /etc/nanobaseai/legacy-llm-env.disabled/qwen36-35b-a3b-mtp.env.* 2>/dev/null | head -1 || true)"
  if [[ -n "${bak}" ]]; then
    cp "${bak}" "${ENVF}"
    echo "restored env from ${bak}"
  elif [[ ! -f "${ENVF}" ]]; then
    cat > "${ENVF}" <<'EOF'
LLAMA_HOST=127.0.0.1
LLAMA_PORT=8010

MODEL_ALIAS=nanobase-qwen36-35b-a3b-mtp
MODEL_FILE=/opt/nanobaseai/models/qwen36-35b-a3b-mtp/Qwen3.6-35B-A3B-UD-Q4_K_XL.gguf

CTX_SIZE=32768
THREADS=24
BATCH=1024
UBATCH=256
PARALLEL=1

MTP_N_MAX=2

CACHE_TYPE_K=q4_0
CACHE_TYPE_V=q4_0
EOF
    echo "wrote default ${ENVF}"
  fi
  chmod 644 "${ENVF}"
}

write_35b_unit() {
  # Unmask (symlink to /dev/null) then write real unit.
  systemctl unmask "${UNIT}" 2>/dev/null || true
  rm -f "/etc/systemd/system/${UNIT}"
  cat > "/etc/systemd/system/${UNIT}" <<'EOF'
[Unit]
Description=NanobaseAI Qwen3.6-35B-A3B MTP llama.cpp Server
After=network-online.target
Wants=network-online.target
Conflicts=nanobase-qwen3-8b.service nanobase-qwen36-mtp.service nanobase-qwen36-27b.service

[Service]
Type=simple
User=nanobase
Group=nanobase
WorkingDirectory=/opt/nanobaseai
Environment=HOME=/opt/nanobaseai
Environment=XDG_CACHE_HOME=/opt/nanobaseai/.cache
EnvironmentFile=/etc/nanobaseai/qwen36-35b-a3b-mtp.env

ExecStart=/opt/nanobaseai/bin/llama-server \
  --model ${MODEL_FILE} \
  --alias ${MODEL_ALIAS} \
  --host ${LLAMA_HOST} \
  --port ${LLAMA_PORT} \
  -c ${CTX_SIZE} \
  -t ${THREADS} \
  -b ${BATCH} \
  -ub ${UBATCH} \
  -np ${PARALLEL} \
  --cache-type-k ${CACHE_TYPE_K} \
  --cache-type-v ${CACHE_TYPE_V} \
  --spec-type draft-mtp \
  --spec-draft-n-max ${MTP_N_MAX} \
  --mlock \
  --jinja \
  --reasoning off \
  --metrics

Restart=always
RestartSec=5
LimitNOFILE=1048576
LimitMEMLOCK=infinity

[Install]
WantedBy=multi-user.target
EOF
  echo "wrote /etc/systemd/system/${UNIT}"
}

retarget_dependents() {
  local ts
  ts="$(date +%Y%m%d%H%M%S)"

  local dbgpt=/etc/systemd/system/nanobase-dbgpt.service
  if [[ -f "${dbgpt}" ]]; then
    cp "${dbgpt}" "${dbgpt}.bak.${ts}"
    sed -i \
      -e "s/nanobase-qwen3-8b\\.service/${UNIT}/g" \
      -e "s/LLM_MODEL_NAME=nanobase-meeting-8b/LLM_MODEL_NAME=${MODEL_ID}/g" \
      "${dbgpt}"
  fi

  local toml=/data/nanobaseai/bi/frontend/backend/configs/dbgpt-openai-compat.toml
  if [[ -f "${toml}" ]]; then
    cp "${toml}" "${toml}.bak.${ts}"
    sed -i \
      -e "s/nanobase-meeting-8b/${MODEL_ID}/g" \
      -e "s/nanobase-qwen36-mtp/${MODEL_ID}/g" \
      "${toml}"
  fi

  local envf=/data/nanobaseai/bi/frontend/backend/.env
  if [[ -f "${envf}" ]]; then
    if grep -q '^LLM_MODEL_NAME=' "${envf}"; then
      sed -i "s/^LLM_MODEL_NAME=.*/LLM_MODEL_NAME=${MODEL_ID}/" "${envf}"
    else
      echo "LLM_MODEL_NAME=${MODEL_ID}" >> "${envf}"
    fi
  fi

  local qa=/etc/systemd/system/nanobase-qa-api.service
  if [[ -f "${qa}" ]]; then
    cp "${qa}" "${qa}.bak.${ts}"
    sed -i "s/OLLAMA_MODEL=nanobase-meeting-8b/OLLAMA_MODEL=${MODEL_ID}/g" "${qa}"
  fi

  local proxy
  for proxy in nanobase-llm-8015-proxy.service nanobase-llm-docker-proxy.service; do
    local punit=/etc/systemd/system/${proxy}
    [[ -f "${punit}" ]] || continue
    cp "${punit}" "${punit}.bak.${ts}"
    sed -i "s/nanobase-qwen3-8b\\.service/${UNIT}/g" "${punit}"
  done

  local act=/etc/nanobaseai/actenora.env
  if [[ -f "${act}" ]]; then
    cp "${act}" "${act}.bak.${ts}"
    sed -i \
      -e "s/^ACTENORA_AI_PROVIDER_SERVED_MODEL_IDS=.*/ACTENORA_AI_PROVIDER_SERVED_MODEL_IDS=${MODEL_ID}/" \
      -e "s/^ACTENORA_AI_PROVIDER_FAST_EXTRACTION_SERVED_MODEL_ID=.*/ACTENORA_AI_PROVIDER_FAST_EXTRACTION_SERVED_MODEL_ID=${MODEL_ID}/" \
      -e "s/^ACTENORA_AI_PROVIDER_FINAL_SERVED_MODEL_ID=.*/ACTENORA_AI_PROVIDER_FINAL_SERVED_MODEL_ID=${MODEL_ID}/" \
      "${act}"
    if ! grep -q '^ACTENORA_AI_PROVIDER_FAST_EXTRACTION_SERVED_MODEL_ID=' "${act}"; then
      echo "ACTENORA_AI_PROVIDER_FAST_EXTRACTION_SERVED_MODEL_ID=${MODEL_ID}" >> "${act}"
    fi
    if ! grep -q '^ACTENORA_AI_PROVIDER_FINAL_SERVED_MODEL_ID=' "${act}"; then
      echo "ACTENORA_AI_PROVIDER_FINAL_SERVED_MODEL_ID=${MODEL_ID}" >> "${act}"
    fi
    if ! grep -q '^ACTENORA_AI_PROVIDER_SERVED_MODEL_IDS=' "${act}"; then
      echo "ACTENORA_AI_PROVIDER_SERVED_MODEL_IDS=${MODEL_ID}" >> "${act}"
    fi
  fi
}

main() {
  require_root
  stop_and_remove_8b
  restore_35b_env
  write_35b_unit
  retarget_dependents

  # Keep other legacy units masked (27b / old mtp).
  systemctl mask nanobase-qwen36-27b.service nanobase-qwen36-mtp.service 2>/dev/null || true

  systemctl daemon-reload
  systemctl enable "${UNIT}"
  systemctl restart "${UNIT}"
  systemctl try-restart nanobase-dbgpt.service nanobase-qa-api.service \
    nanobase-llm-8015-proxy.service nanobase-llm-docker-proxy.service || true

  echo
  echo "Waiting for :8010 /v1/models (35B load can take several minutes)..."
  local i=0
  local id=""
  while [[ "${i}" -lt 120 ]]; do
    id="$(curl -sf --max-time 2 http://127.0.0.1:8010/v1/models 2>/dev/null \
      | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"][0]["id"])' 2>/dev/null || true)"
    if [[ "${id}" == "${MODEL_ID}" ]]; then
      break
    fi
    sleep 5
    i=$((i + 1))
    if (( i % 6 == 0 )); then
      echo "  still loading... ($(systemctl is-active "${UNIT}") ${i}0s)"
    fi
  done

  echo "Active LLM unit: $(systemctl is-active "${UNIT}")"
  echo "Served model id: ${id:-unavailable}"
  echo "8B unit present: $(test -f /etc/systemd/system/${EIGHT_B_UNIT} && echo yes || echo no)"
  [[ "${id}" == "${MODEL_ID}" ]] || {
    echo "ERROR: expected ${MODEL_ID} on :8010" >&2
    systemctl status "${UNIT}" --no-pager -l | head -40 >&2 || true
    exit 1
  }
}

main "$@"
