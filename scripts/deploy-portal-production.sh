#!/usr/bin/env bash
# Full portal.nanobase.ai deploy: QA hub (mobile-qa) + EasyMeeting SPA under /easymeeting/
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

SSH_HOST="${ACTENORA_PORTAL_SSH_HOST:-nanobase}"
MOBILE_QA_ROOT="${NANOBASE_MOBILE_QA_ROOT:-/data/nanobaseai/mobile-qa}"
SKIP_QA_PORTAL="${SKIP_QA_PORTAL_DEPLOY:-0}"
SKIP_PORTAL_E2E="${SKIP_PORTAL_E2E:-1}"

patch_qa_portal_rsync() {
  log "Ensuring QA portal deploy preserves /easymeeting/ (rsync exclude)"
  ssh "${SSH_HOST}" bash -s <<EOF
set -euo pipefail
DEPLOY="${MOBILE_QA_ROOT}/scripts/server/deploy-qa-portal.sh"
if [[ ! -f "\$DEPLOY" ]]; then
  echo "WARN: \$DEPLOY not found — skipping rsync exclude patch"
  exit 0
fi
if grep -q "exclude 'easymeeting/'" "\$DEPLOY"; then
  echo "QA deploy script already excludes easymeeting/"
else
  # Keep legacy actenora/ exclude if present; always preserve easymeeting/.
  if grep -q "exclude 'actenora/'" "\$DEPLOY"; then
    sed -i "s|--exclude 'actenora/'|--exclude 'actenora/' --exclude 'easymeeting/'|g" "\$DEPLOY"
  else
    sed -i "s|--exclude 'config.json'|--exclude 'config.json' --exclude 'easymeeting/'|g" "\$DEPLOY"
  fi
  echo "Patched \$DEPLOY to exclude easymeeting/"
fi
EOF
}

deploy_qa_portal() {
  log "Deploying QA portal hub on ${SSH_HOST} (SKIP_PORTAL_E2E=${SKIP_PORTAL_E2E})"
  ssh "${SSH_HOST}" "cd '${MOBILE_QA_ROOT}' && SKIP_PORTAL_E2E='${SKIP_PORTAL_E2E}' bash scripts/server/deploy-qa-portal.sh"
}

main() {
  patch_qa_portal_rsync
  if [[ "${SKIP_QA_PORTAL}" != "1" ]]; then
    deploy_qa_portal
  else
    log "Skipping QA portal deploy (SKIP_QA_PORTAL_DEPLOY=1)"
  fi
  bash "${SCRIPT_DIR}/deploy-actenora-portal.sh"
  log "Production portal deploy finished: https://portal.nanobase.ai/ (+ /easymeeting/)"
}

main "$@"
