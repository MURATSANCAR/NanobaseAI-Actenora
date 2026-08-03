#!/usr/bin/env bash
# Run one standup eval on nanobase and dump rich artifacts into a local run dir.
# Usage (from repo root, AFTER the desired commit is deployed):
#   TREE=B_HEAD_f9c699f RUN=01 COMMIT=f9c699f bash scripts/eval-standup-ba-run.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TREE="${TREE:?TREE required e.g. B_HEAD_f9c699f}"
RUN="${RUN:?RUN required e.g. 01}"
COMMIT="${COMMIT:?COMMIT required}"
SSH_HOST="${ACTENORA_BACKEND_SSH_HOST:-nanobase}"
OUT_ROOT="${OUT_ROOT:-$ROOT/artifacts/eval/standup-v0.1}"
RUN_DIR="$OUT_ROOT/$TREE/run_$RUN"
VTT_LOCAL="$ROOT/modules/ai-processing/src/test/resources/aiprocessing/eval/01_15dk_daily_standup.vtt"
REMOTE_VTT_DIR="/tmp/actenora-eval-vtts"
REMOTE_OUT="/tmp/actenora-standup-ba/$TREE/run_$RUN"

mkdir -p "$RUN_DIR"

echo "== standup BA run TREE=$TREE RUN=$RUN COMMIT=$COMMIT =="
date -u +%Y-%m-%dT%H:%M:%SZ | tee "$RUN_DIR/started_at.txt"

# Ensure VTT on remote
ssh "$SSH_HOST" "mkdir -p '$REMOTE_VTT_DIR' '$REMOTE_OUT'"
scp -q "$VTT_LOCAL" "$SSH_HOST:$REMOTE_VTT_DIR/01_15dk_daily_standup.vtt"

# Snapshot env (non-secret)
ssh "$SSH_HOST" "docker exec actenora-prodlike-platform-backend printenv" \
  | grep -iE 'ACTENORA_AI_|ACTENORA_MEETING_SIGNAL|FINALIZATION|PIPELINE|SERVED_MODEL|PROVIDER_' \
  | sed -E 's/(SECRET|PASSWORD|KEY|TOKEN)=.*/\1=***/i' \
  | sort > "$RUN_DIR/env.snapshot.txt"

# Copy suite script to remote and run ONLY standup
scp -q "$ROOT/scripts/run-realistic-vtt-suite.sh" "$SSH_HOST:/tmp/run-realistic-vtt-suite.sh"
ssh "$SSH_HOST" "chmod +x /tmp/run-realistic-vtt-suite.sh
  export ONLY=01_15dk_daily_standup.vtt
  export VTT_DIR='$REMOTE_VTT_DIR'
  export OUT_DIR='$REMOTE_OUT'
  export BASE=http://127.0.0.1:8088
  bash /tmp/run-realistic-vtt-suite.sh
" | tee "$RUN_DIR/suite.log"

# Pull suite outputs
scp -q "$SSH_HOST:$REMOTE_OUT/01_15dk_daily_standup.ids" "$RUN_DIR/" 2>/dev/null || true
scp -q "$SSH_HOST:$REMOTE_OUT/01_15dk_daily_standup.result.txt" "$RUN_DIR/" 2>/dev/null || true
scp -q "$SSH_HOST:$REMOTE_OUT/summary.tsv" "$RUN_DIR/" 2>/dev/null || true

# Enrich from DB if ids present
if [[ -f "$RUN_DIR/01_15dk_daily_standup.ids" ]]; then
  # shellcheck disable=SC1090
  # file format: MEETING=... TR=... JOB=...
  MEETING=$(sed -n 's/.*MEETING=\([^ ]*\).*/\1/p' "$RUN_DIR/01_15dk_daily_standup.ids" | head -1)
  TR=$(sed -n 's/.*TR=\([^ ]*\).*/\1/p' "$RUN_DIR/01_15dk_daily_standup.ids" | head -1)
  JOB=$(sed -n 's/.*JOB=\([^ ]*\).*/\1/p' "$RUN_DIR/01_15dk_daily_standup.ids" | head -1)
  python3 - <<PY | tee "$RUN_DIR/meta.json"
import json
print(json.dumps({
  "runId": "${TREE}_run_${RUN}",
  "tree": "$TREE",
  "commit": "$COMMIT",
  "meetingOccurrenceId": "$MEETING",
  "transcriptId": "$TR",
  "jobId": "$JOB",
  "pipelineMode": "legacy",
  "finalizationMode": "editorial",
}, indent=2))
PY
  ssh "$SSH_HOST" "MEETING='$MEETING' JOB='$JOB' bash -s" <<'EOS' > "$RUN_DIR/final.note.json"
set -euo pipefail
docker exec -i actenora-prodlike-postgres psql -U actenora -d actenora -v ON_ERROR_STOP=1 -At <<SQL
SELECT json_build_object(
  'meetingOccurrenceId', n.meeting_occurrence_id,
  'noteId', n.id,
  'reviewStatus', n.review_status,
  'versionId', n.current_version_id,
  'executiveSummary', v.executive_summary,
  'aiConfidence', v.ai_confidence,
  'modelId', v.model_id,
  'promptVersionId', v.prompt_version_id,
  'job', (SELECT json_build_object('id', j.id, 'status', j.status, 'attemptCount', j.attempt_count,
            'errorCode', j.error_code, 'startedAt', j.started_at, 'completedAt', j.completed_at,
            'promptVersion', j.prompt_version, 'stage', j.stage)
          FROM aiprocessing.ai_jobs j WHERE j.id = '$JOB'),
  'decisions', coalesce((SELECT json_agg(json_build_object('text', d.text, 'confidence', d.ai_confidence) ORDER BY d.created_at)
               FROM meetingintelligence.decisions d WHERE d.note_version_id = n.current_version_id), '[]'::json),
  'actionItems', coalesce((SELECT json_agg(json_build_object('owner', a.owner, 'text', a.text, 'dueDate', a.due_date, 'confidence', a.ai_confidence) ORDER BY a.created_at)
               FROM meetingintelligence.action_items a WHERE a.note_version_id = n.current_version_id), '[]'::json),
  'risks', coalesce((SELECT json_agg(json_build_object('text', r.text, 'confidence', r.ai_confidence) ORDER BY r.created_at)
               FROM meetingintelligence.risks r WHERE r.note_version_id = n.current_version_id), '[]'::json),
  'proposals', coalesce((SELECT json_agg(json_build_object('text', p.text) ORDER BY p.created_at)
               FROM meetingintelligence.proposals p WHERE p.note_version_id = n.current_version_id), '[]'::json),
  'openQuestions', coalesce((SELECT json_agg(json_build_object('text', o.text) ORDER BY o.created_at)
               FROM meetingintelligence.open_questions o WHERE o.note_version_id = n.current_version_id), '[]'::json),
  'commitments', coalesce((SELECT json_agg(json_build_object('owner', c.owner, 'text', c.text) ORDER BY c.created_at)
               FROM meetingintelligence.commitments c WHERE c.note_version_id = n.current_version_id), '[]'::json),
  'importantFacts', coalesce((SELECT json_agg(json_build_object('text', f.text) ORDER BY f.created_at)
               FROM meetingintelligence.important_facts f WHERE f.note_version_id = n.current_version_id), '[]'::json),
  'qualityFlags', coalesce((SELECT json_agg(json_build_object('code', q.code, 'detail', q.detail) ORDER BY q.code)
               FROM meetingintelligence.quality_flags q WHERE q.note_version_id = n.current_version_id), '[]'::json)
)
FROM meetingintelligence.meeting_notes n
JOIN meetingintelligence.meeting_note_versions v ON v.id = n.current_version_id
WHERE n.meeting_occurrence_id = '$MEETING'
LIMIT 1;
SQL
EOS
fi

date -u +%Y-%m-%dT%H:%M:%SZ | tee "$RUN_DIR/finished_at.txt"
echo "Wrote $RUN_DIR"
