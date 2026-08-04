#!/usr/bin/env bash
# Nanobase eval-only lineage re-run campaign (screen-safe).
# Does NOT touch a separate production cluster; swaps eval compose app.jar (prod-like)
# and restores backup when finished.
set -euo pipefail

EVAL_ROOT="${EVAL_ROOT:-/data/nanobaseai/actenora/eval/standup-ba/phase1b/lineage-rerun}"
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
B_JAR="${B_JAR:-$JAR_DIR/B_LINEAGE_f9c699f.jar}"
A_JAR="${A_JAR:-$JAR_DIR/A_LINEAGE_472172a.jar}"
BACKUP_JAR="$EVAL_ROOT/backup-app.jar"

mkdir -p "$EVAL_ROOT/manifests" "$EVAL_ROOT/B_LINEAGE_f9c699f" "$EVAL_ROOT/A_LINEAGE_472172a"
exec >>"$LOG" 2>&1

ts() { date -u +%Y-%m-%dT%H:%M:%SZ; }
status() { printf '%s\n' "$1" | tee "$STATUS"; }

echo "==== lineage-rerun campaign start $(ts) pid=$$ host=$(hostname) ===="
status "BOOT at=$(ts)"

# Eval-only env: copy secrets file and force lineage on (no tokens written to artifacts)
cp -f "$REMOTE_ENV" "$LINEAGE_ENV"
if ! grep -q '^ACTENORA_MEETING_LINEAGE_ENABLED=' "$LINEAGE_ENV"; then
  echo 'ACTENORA_MEETING_LINEAGE_ENABLED=true' >>"$LINEAGE_ENV"
else
  sed -i 's/^ACTENORA_MEETING_LINEAGE_ENABLED=.*/ACTENORA_MEETING_LINEAGE_ENABLED=true/' "$LINEAGE_ENV"
fi
if ! grep -q '^ACTENORA_AI_PIPELINE_LINEAGE_RECORDING=' "$LINEAGE_ENV"; then
  echo 'ACTENORA_AI_PIPELINE_LINEAGE_RECORDING=true' >>"$LINEAGE_ENV"
fi

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

write_run_manifest() {
  local dir="$1" tree="$2" run="$3" commit="$4" status="$5"
  python3 - <<PY >"$dir/run-manifest.json"
import json
print(json.dumps({
  "runId": f"${tree}_${run}",
  "baseline": "B" if "${tree}".startswith("B_") else "A",
  "commit": "$commit",
  "status": "$status",
  "lineageEnabled": True,
  "productionDeploy": False,
  "artifacts": {
    "final-note.json": "AVAILABLE" if __import__('pathlib').Path("$dir/final.note.json").exists() else "NOT_AVAILABLE",
    "lineage.json": "AVAILABLE" if __import__('pathlib').Path("$dir/lineage.json").exists() and __import__('pathlib').Path("$dir/lineage.json").stat().st_size>5 else "NOT_AVAILABLE",
    "raw-extractions.json": "NOT_AVAILABLE",
    "gate-decisions.json": "NOT_AVAILABLE",
    "chunks.json": "NOT_AVAILABLE",
    "merged-bundle.json": "NOT_AVAILABLE",
    "validated-bundle.json": "NOT_AVAILABLE",
  }
}, indent=2))
PY
}

deploy_jar() {
  local jar="$1" label="$2"
  echo "==== Deploy EVAL $label from $jar $(ts) ===="
  status "DEPLOYING_${label} at=$(ts)"
  [[ -f "$jar" ]] || { echo "missing jar $jar"; status "FAILED_MISSING_JAR_${label} at=$(ts)"; exit 1; }
  # backup once
  if [[ ! -f "$BACKUP_JAR" && -f "$COMPOSE_DIR/app.jar" ]]; then
    cp -f "$COMPOSE_DIR/app.jar" "$BACKUP_JAR"
    sha256sum "$BACKUP_JAR" | tee "$EVAL_ROOT/backup-app.jar.sha256"
  fi
  cp -f "$jar" "$COMPOSE_DIR/app.jar"
  cd "$COMPOSE_DIR"
  sudo docker compose \
    -f docker-compose.prod-like.yml \
    -f docker-compose.portal-server.override.yml \
    --env-file "$LINEAGE_ENV" \
    up -d --build --no-deps --force-recreate platform-backend
  for i in $(seq 1 90); do
    if curl -sf -o /dev/null http://127.0.0.1:8088/actuator/health/liveness; then
      echo "backend up after ${i}s"
      docker exec actenora-prodlike-platform-backend printenv | grep -E 'LINEAGE' || true
      break
    fi
    sleep 2
  done
  curl -sf -o /dev/null http://127.0.0.1:8088/actuator/health/liveness || {
    status "FAILED_DEPLOY_${label} at=$(ts)"; exit 1;
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
  fi
}

run_one() {
  local tree="$1" run="$2" commit="$3"
  local dir="$EVAL_ROOT/$tree/$run"
  local remote_out="/tmp/actenora-lineage-rerun/$tree/$run"
  if [[ -f "$dir/final.note.json" && -f "$dir/lineage.json" ]]; then
    echo "skip $tree $run (artifacts exist)"
    return 0
  fi
  rm -rf "$dir"
  mkdir -p "$dir" "$remote_out"
  echo "==== START $tree $run $(ts) ===="
  status "RUNNING_${tree}_${run} at=$(ts)"
  date -u +%Y-%m-%dT%H:%M:%SZ | tee "$dir/started_at.txt"
  docker exec actenora-prodlike-platform-backend printenv \
    | grep -iE 'ACTENORA_AI_|ACTENORA_MEETING|FINALIZATION|PIPELINE|SERVED_MODEL|LINEAGE' \
    | sed -E 's/(SECRET|PASSWORD|KEY|TOKEN)=.*/\1=***/i' \
    | sort >"$dir/env.snapshot.txt" || true

  ONLY=01_15dk_daily_standup.vtt VTT_DIR="$VTT_DIR" OUT_DIR="$remote_out" BASE="$BASE" \
    bash "$SUITE" | tee "$dir/suite.log"

  cp -f "$remote_out/01_15dk_daily_standup.ids" "$dir/" 2>/dev/null || true
  if [[ ! -f "$dir/01_15dk_daily_standup.ids" ]]; then
    status "FAILED_${tree}_${run}_no_ids at=$(ts)"; exit 1
  fi
  MEETING=$(sed -n 's/.*MEETING=\([^ ]*\).*/\1/p' "$dir/01_15dk_daily_standup.ids" | head -1)
  TR=$(sed -n 's/.*TR=\([^ ]*\).*/\1/p' "$dir/01_15dk_daily_standup.ids" | head -1)
  JOB=$(sed -n 's/.*JOB=\([^ ]*\).*/\1/p' "$dir/01_15dk_daily_standup.ids" | head -1)
  python3 - <<PY >"$dir/meta.json"
import json
print(json.dumps({
  "runId": "${tree}_${run}",
  "tree": "$tree",
  "commit": "$commit",
  "meetingOccurrenceId": "$MEETING",
  "transcriptId": "$TR",
  "jobId": "$JOB",
  "lineageEnabled": True,
  "finalizationMode": "editorial",
}, indent=2))
PY
  dump_final_note "$MEETING" "$JOB" "$dir/final.note.json"
  dump_lineage "$JOB" "$dir/lineage.json"
  # also write lineage.jsonl if events present
  python3 - <<PY
import json
from pathlib import Path
p=Path("$dir/lineage.json")
try:
  data=json.loads(p.read_text() or "{}")
except Exception:
  data={}
events=data.get("events") or []
out=Path("$dir/lineage.jsonl")
with out.open("w") as f:
  for e in events:
    f.write(json.dumps(e, ensure_ascii=False)+"\n")
Path("$dir/ARTIFACT_AVAILABILITY.json").write_text(json.dumps({
  "final-note.json": "AVAILABLE" if Path("$dir/final.note.json").stat().st_size>10 else "NOT_AVAILABLE",
  "lineage.json": "AVAILABLE" if events else "NOT_AVAILABLE",
  "lineage.jsonl": "AVAILABLE" if events else "NOT_AVAILABLE",
  "raw-extractions.json": "NOT_AVAILABLE",
  "gate-decisions.json": "NOT_AVAILABLE",
  "chunks.json": "NOT_AVAILABLE",
  "merged-bundle.json": "NOT_AVAILABLE",
  "validated-bundle.json": "NOT_AVAILABLE",
}, indent=2)+"\n")
PY
  write_run_manifest "$dir" "$tree" "$run" "$commit" "COMPLETED"
  date -u +%Y-%m-%dT%H:%M:%SZ | tee "$dir/finished_at.txt"
  echo "==== DONE $tree $run $(ts) events=$(wc -l < "$dir/lineage.jsonl" | tr -d ' ') ===="
}

smoke_ok() {
  local dir="$1"
  [[ -s "$dir/final.note.json" ]] || return 1
  python3 - <<PY
import json,sys
from pathlib import Path
d=Path("$dir")
note=json.loads((d/"final.note.json").read_text())
lin=json.loads((d/"lineage.json").read_text() or "{}")
events=lin.get("events") or []
# Smoke: pipeline completed + lineage artifact parseable. Event count may be 0 if hooks missed — fail soft with warning.
print("smoke_note_actions", len(note.get("actionItems") or []))
print("smoke_lineage_events", len(events))
if not note.get("decisions") and not note.get("actionItems"):
  sys.exit(2)
PY
}

# --- main ---
[[ -f "$B_JAR" ]] || { status "WAITING_FOR_B_JAR at=$(ts)"; echo "missing $B_JAR"; exit 1; }
[[ -f "$A_JAR" ]] || { status "WAITING_FOR_A_JAR at=$(ts)"; echo "missing $A_JAR"; exit 1; }
cp -f "$JAR_DIR"/B_LINEAGE_f9c699f.build.json "$EVAL_ROOT/manifests/" 2>/dev/null || true
cp -f "$JAR_DIR"/A_LINEAGE_472172a.build.json "$EVAL_ROOT/manifests/" 2>/dev/null || true

deploy_jar "$B_JAR" B_LINEAGE_f9c699f
run_one B_LINEAGE_f9c699f smoke_01 f9c699f
if ! smoke_ok "$EVAL_ROOT/B_LINEAGE_f9c699f/smoke_01"; then
  status "FAILED_B_SMOKE at=$(ts)"; restore_backup_jar; exit 1
fi
status "B_SMOKE_OK at=$(ts)"
for n in 01 02 03 04 05; do
  run_one B_LINEAGE_f9c699f "run_$n" f9c699f
done

deploy_jar "$A_JAR" A_LINEAGE_472172a
run_one A_LINEAGE_472172a smoke_01 472172a
if ! smoke_ok "$EVAL_ROOT/A_LINEAGE_472172a/smoke_01"; then
  status "FAILED_A_SMOKE at=$(ts)"; restore_backup_jar; exit 1
fi
status "A_SMOKE_OK at=$(ts)"
for n in 01 02 03 04 05; do
  run_one A_LINEAGE_472172a "run_$n" 472172a
done

# Post analysis if helper present
if [[ -f "$SCRIPTS_DIR/phase1b/analyze-cue-compound-traces.py" ]]; then
  python3 "$SCRIPTS_DIR/phase1b/analyze-cue-compound-traces.py" --tree-root "$EVAL_ROOT" || true
fi

restore_backup_jar
status "COMPLETE at=$(ts)"
echo "==== lineage-rerun campaign COMPLETE $(ts) ===="
