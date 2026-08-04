#!/usr/bin/env bash
# Nanobase eval-only C_CANDIDATE_ACTION_TITLE_CONTEXT campaign (5 real LLM runs).
# Swaps eval compose app.jar and restores backup when finished. No production deploy.
set -euo pipefail

EVAL_ROOT="${EVAL_ROOT:-/data/nanobaseai/actenora/eval/standup-ba/phase1b/C_CANDIDATE_ACTION_TITLE_CONTEXT}"
SCRIPTS_DIR="${SCRIPTS_DIR:-/data/nanobaseai/actenora/eval/standup-ba}"
JAR_DIR="${JAR_DIR:-$SCRIPTS_DIR/jars}"
VTT_DIR="${VTT_DIR:-$SCRIPTS_DIR/vtts}"
SUITE="$SCRIPTS_DIR/run-realistic-vtt-suite.sh"
COMPOSE_DIR="${COMPOSE_DIR:-/data/nanobaseai/actenora/infrastructure/compose}"
REMOTE_ENV="${REMOTE_ENV:-/etc/nanobaseai/actenora.env}"
LINEAGE_ENV="$EVAL_ROOT/actenora.lineage.env"
BASE="${BASE:-http://127.0.0.1:8088}"
LOG="$EVAL_ROOT/campaign.log"
STATUS="$EVAL_ROOT/campaign.STATUS.txt"
C_JAR="${C_JAR:-$JAR_DIR/C_CANDIDATE_ACTION_TITLE_CONTEXT.jar}"
BACKUP_JAR="$EVAL_ROOT/backup-app.jar"
TREE="C_CANDIDATE_ACTION_TITLE_CONTEXT"
COMMIT="472172a"

mkdir -p "$EVAL_ROOT" "$EVAL_ROOT/$TREE"
exec >>"$LOG" 2>&1

ts() { date -u +%Y-%m-%dT%H:%M:%SZ; }
status() { printf '%s\n' "$1" | tee "$STATUS"; }

echo "==== C candidate campaign start $(ts) pid=$$ host=$(hostname) ===="
status "BOOT at=$(ts)"

sudo cp -f "$REMOTE_ENV" "$LINEAGE_ENV"
sudo chown "$(id -u):$(id -g)" "$LINEAGE_ENV"
chmod 600 "$LINEAGE_ENV"
if ! grep -q '^ACTENORA_MEETING_LINEAGE_ENABLED=' "$LINEAGE_ENV"; then
  echo 'ACTENORA_MEETING_LINEAGE_ENABLED=true' >>"$LINEAGE_ENV"
else
  sed -i 's/^ACTENORA_MEETING_LINEAGE_ENABLED=.*/ACTENORA_MEETING_LINEAGE_ENABLED=true/' "$LINEAGE_ENV"
fi
if ! grep -q '^ACTENORA_AI_PIPELINE_LINEAGE_RECORDING=' "$LINEAGE_ENV"; then
  echo 'ACTENORA_AI_PIPELINE_LINEAGE_RECORDING=true' >>"$LINEAGE_ENV"
else
  sed -i 's/^ACTENORA_AI_PIPELINE_LINEAGE_RECORDING=.*/ACTENORA_AI_PIPELINE_LINEAGE_RECORDING=true/' "$LINEAGE_ENV"
fi
chmod 600 "$LINEAGE_ENV"

psql() { docker exec -i actenora-prodlike-postgres psql -U actenora -d actenora "$@"; }

dump_final_note() {
  local meeting="$1" job="$2" out="$3"
  MEETING="$meeting" JOB="$job" psql -v ON_ERROR_STOP=1 -At <<SQL >"$out"
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
  'actionItems', coalesce((SELECT json_agg(json_build_object('owner', a.owner, 'text', a.text, 'dueDate', a.due_date,
            'relativeDate', a.relative_date, 'dueAt', a.due_at, 'confidence', a.ai_confidence) ORDER BY a.created_at)
               FROM meetingintelligence.action_items a WHERE a.note_version_id = n.current_version_id), '[]'::json),
  'risks', coalesce((SELECT json_agg(json_build_object('text', r.text, 'confidence', r.ai_confidence) ORDER BY r.created_at)
               FROM meetingintelligence.risks r WHERE r.note_version_id = n.current_version_id), '[]'::json),
  'proposals', coalesce((SELECT json_agg(json_build_object('text', p.text) ORDER BY p.created_at)
               FROM meetingintelligence.proposals p WHERE p.note_version_id = n.current_version_id), '[]'::json),
  'openQuestions', coalesce((SELECT json_agg(json_build_object('text', o.text) ORDER BY o.created_at)
               FROM meetingintelligence.open_questions o WHERE o.note_version_id = n.current_version_id), '[]'::json),
  'commitments', coalesce((SELECT json_agg(json_build_object('owner', c.owner, 'text', c.text) ORDER BY c.created_at)
               FROM meetingintelligence.commitments c WHERE c.note_version_id = n.current_version_id), '[]'::json),
  'qualityFlags', coalesce((SELECT json_agg(json_build_object('code', q.code, 'detail', q.detail) ORDER BY q.code)
               FROM meetingintelligence.quality_flags q WHERE q.note_version_id = n.current_version_id), '[]'::json)
)
FROM meetingintelligence.meeting_notes n
JOIN meetingintelligence.meeting_note_versions v ON v.id = n.current_version_id
WHERE n.meeting_occurrence_id = '$MEETING'
LIMIT 1;
SQL
}

dump_lineage() {
  local job="$1" out="$2"
  JOB="$job" psql -v ON_ERROR_STOP=1 -At <<SQL >"$out"
SELECT coalesce(payload_json::text, '')
FROM aiprocessing.processing_artifact
WHERE job_id = '$JOB' AND artifact_type = 'item-lineage'
ORDER BY created_at DESC
LIMIT 1;
SQL
  if [[ ! -s "$out" ]]; then
    echo '{"schemaVersion":"1.0","events":[],"status":"NOT_AVAILABLE"}' >"$out"
  fi
}

dump_action_post_processing() {
  local job="$1" out="$2"
  JOB="$job" psql -v ON_ERROR_STOP=1 -At <<SQL >"$out"
SELECT coalesce(payload_json::text, '')
FROM aiprocessing.processing_artifact
WHERE job_id = '$JOB' AND artifact_type = 'action-post-processing'
ORDER BY created_at DESC
LIMIT 1;
SQL
  if [[ ! -s "$out" ]]; then
    echo '{"status":"NOT_AVAILABLE"}' >"$out"
  fi
}

deploy_jar() {
  local jar="$1" label="$2"
  echo "==== Deploy EVAL $label from $jar $(ts) ===="
  status "DEPLOYING_${label} at=$(ts)"
  [[ -f "$jar" ]] || { echo "missing jar $jar"; status "FAILED_MISSING_JAR at=$(ts)"; exit 1; }
  if [[ ! -f "$BACKUP_JAR" && -f "$COMPOSE_DIR/app.jar" ]]; then
    cp -f "$COMPOSE_DIR/app.jar" "$BACKUP_JAR"
    sha256sum "$BACKUP_JAR" | tee "$EVAL_ROOT/backup-app.jar.sha256"
  fi
  cp -f "$jar" "$COMPOSE_DIR/app.jar"
  cd "$COMPOSE_DIR"
  sudo docker compose \
    -f docker-compose.prod-like.yml \
    -f docker-compose.portal-server.override.yml \
    -f /data/nanobaseai/actenora/eval/standup-ba/phase1b/docker-compose.lineage-eval.override.yml \
    --env-file "$LINEAGE_ENV" \
    up -d --build --no-deps --force-recreate platform-backend
  for i in $(seq 1 90); do
    if curl -sf -o /dev/null http://127.0.0.1:8088/actuator/health/liveness; then
      echo "backend up after ${i}s"
      docker exec actenora-prodlike-platform-backend printenv | grep -E 'LINEAGE' || {
        echo "ERROR: LINEAGE env not present in container"
        status "FAILED_LINEAGE_ENV at=$(ts)"; exit 1;
      }
      break
    fi
    sleep 2
  done
  curl -sf -o /dev/null http://127.0.0.1:8088/actuator/health/liveness || {
    status "FAILED_DEPLOY at=$(ts)"; exit 1;
  }
  sha256sum "$COMPOSE_DIR/app.jar" | tee "$EVAL_ROOT/deploy_${label}.sha256"
}

restore_backup_jar() {
  if [[ -f "$BACKUP_JAR" ]]; then
    echo "==== Restore backup app.jar $(ts) ===="
    cp -f "$BACKUP_JAR" "$COMPOSE_DIR/app.jar"
    cd "$COMPOSE_DIR"
    sudo docker compose \
      -f docker-compose.prod-like.yml \
      -f docker-compose.portal-server.override.yml \
      --env-file "$REMOTE_ENV" \
      up -d --build --no-deps --force-recreate platform-backend || true
    sha256sum "$COMPOSE_DIR/app.jar" | tee "$EVAL_ROOT/restored-app.jar.sha256"
  fi
}

run_one() {
  local run="$1"
  local dir="$EVAL_ROOT/$TREE/run_$run"
  local remote_out="/tmp/actenora-c-candidate/$TREE/run_$run"
  if [[ -f "$dir/final.note.json" ]]; then
    echo "skip run_$run (artifacts exist)"
    return 0
  fi
  rm -rf "$dir"
  mkdir -p "$dir" "$remote_out"
  echo "==== START $TREE run_$run $(ts) ===="
  status "RUNNING_run_${run} at=$(ts)"
  date -u +%Y-%m-%dT%H:%M:%SZ | tee "$dir/started_at.txt"
  docker exec actenora-prodlike-platform-backend printenv \
    | grep -iE 'ACTENORA_AI_|ACTENORA_MEETING|FINALIZATION|PIPELINE|SERVED_MODEL|LINEAGE' \
    | sed -E 's/(SECRET|PASSWORD|KEY|TOKEN)=.*/\1=***/i' \
    | sort >"$dir/env.snapshot.txt" || true

  ONLY=01_15dk_daily_standup.vtt VTT_DIR="$VTT_DIR" OUT_DIR="$remote_out" BASE="$BASE" \
    bash "$SUITE" | tee "$dir/suite.log"

  cp -f "$remote_out/01_15dk_daily_standup.ids" "$dir/" 2>/dev/null || true
  if [[ ! -f "$dir/01_15dk_daily_standup.ids" ]]; then
    status "FAILED_run_${run}_no_ids at=$(ts)"; exit 1
  fi
  MEETING=$(sed -n 's/.*MEETING=\([^ ]*\).*/\1/p' "$dir/01_15dk_daily_standup.ids" | head -1)
  TR=$(sed -n 's/.*TR=\([^ ]*\).*/\1/p' "$dir/01_15dk_daily_standup.ids" | head -1)
  JOB=$(sed -n 's/.*JOB=\([^ ]*\).*/\1/p' "$dir/01_15dk_daily_standup.ids" | head -1)
  python3 - <<PY >"$dir/run-manifest.json"
import json
print(json.dumps({
  "runId": "${TREE}_run_${run}",
  "candidate": "$TREE",
  "commit": "$COMMIT",
  "meetingOccurrenceId": "$MEETING",
  "transcriptId": "$TR",
  "jobId": "$JOB",
  "lineageEnabled": True,
  "finalizationMode": "editorial",
  "productionDeploy": False,
}, indent=2))
PY
  dump_final_note "$MEETING" "$JOB" "$dir/final-note.json"
  # also keep alias used by older scripts
  cp -f "$dir/final-note.json" "$dir/final.note.json"
  dump_lineage "$JOB" "$dir/lineage.json"
  dump_action_post_processing "$JOB" "$dir/action-post-processing.json"
  # runtime metrics from job
  JOB="$JOB" psql -v ON_ERROR_STOP=1 -At <<SQL >"$dir/runtime-metrics.json"
SELECT json_build_object(
  'jobId', j.id,
  'status', j.status,
  'attemptCount', j.attempt_count,
  'startedAt', j.started_at,
  'completedAt', j.completed_at,
  'durationMs', CASE WHEN j.started_at IS NOT NULL AND j.completed_at IS NOT NULL
    THEN EXTRACT(EPOCH FROM (j.completed_at - j.started_at))*1000 ELSE NULL END
)
FROM aiprocessing.ai_jobs j WHERE j.id = '$JOB';
SQL
  date -u +%Y-%m-%dT%H:%M:%SZ | tee "$dir/finished_at.txt"
  echo "==== DONE run_$run $(ts) ===="
}

trap 'status "TRAP_RESTORE at=$(ts)"; restore_backup_jar; status "RESTORED at=$(ts)"' EXIT

deploy_jar "$C_JAR" "C_CANDIDATE"
for r in 01 02 03 04 05; do
  run_one "$r"
done
status "COMPLETE at=$(ts)"
echo "==== C candidate campaign complete $(ts) ===="
