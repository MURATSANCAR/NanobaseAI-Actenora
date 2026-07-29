#!/usr/bin/env bash
# Sequential realistic VTT eval suite against nanobase prodlike.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:8088}"
TENANT="${TENANT:-a99ddd05-a8be-4fb2-a0d3-8db4878e25a0}"
CTX="${CTX:-11111111-1111-1111-1111-111111111111}"
MODEL="${MODEL:-48c350f0-b3da-41aa-8df2-68e6f7362eb8}"
DEP="${DEP:-66f04358-2e5e-4beb-84f9-1b64212a2a58}"
VTT_DIR="${VTT_DIR:-/tmp/actenora-eval-vtts}"
OUT_DIR="${OUT_DIR:-/tmp/actenora-realistic-suite}"
mkdir -p "$OUT_DIR"
SUMMARY="$OUT_DIR/summary.tsv"
echo -e "file\tmeeting\tnote\tjob_status\treview\tconf\td\ta\tr\tp\toq\tflags_snip" > "$SUMMARY"

AUTH_HDR=(
  -H "X-Actenora-Entra-Oid: 8d69a8bc-0165-4fd3-ba31-541933b5d1f0"
  -H "X-Actenora-Entra-Tid: 2d0c9d71-6ec8-4363-b5f7-4544ce7c7a27"
  -H "X-Actenora-Email: muratsancar@nanobase.ai"
  -H "X-Actenora-Display-Name: Murat Sancar"
  -H "X-Actenora-Global-Admin: true"
  -H "X-Actenora-Tenant-Id: ${TENANT}"
)

FILES=(
  "01_15dk_daily_standup.vtt:15"
  "02_30dk_incident_triage.vtt:30"
  "03_1saat_sprint_planning.vtt:60"
  "04_2saat_architecture_review.vtt:120"
  "05_3saat_customer_discovery_roadmap.vtt:180"
  "06_4saat_release_readiness.vtt:240"
  "07_5saat_quarterly_strategy.vtt:300"
)

# Optional filter: ONLY=02_30dk_incident_triage.vtt
ONLY="${ONLY:-}"

psql() {
  docker exec actenora-prodlike-postgres psql -U actenora -d actenora "$@"
}

wait_job() {
  local job="$1"
  local meeting="$2"
  local max="${3:-180}"
  local i row note
  for i in $(seq 1 "$max"); do
    psql -q -c "UPDATE modelmanagement.model_deployment SET status='HEALTHY', last_heartbeat_at=now();
      UPDATE aiprocessing.ai_jobs SET deadline_at=now()+interval '48 hours'
      WHERE id='$job' AND status IN ('QUEUED','RUNNING');" >/dev/null 2>&1 || true
    row=$(psql -Atc "SELECT status||'|'||coalesce(attempt_count::text,'0') FROM aiprocessing.ai_jobs WHERE id='$job'")
    note=$(psql -Atc "
      SELECT coalesce(n.current_version_number::text,'-')||'|'||coalesce(n.review_status,'-')||'|'||coalesce(v.ai_confidence::text,'-')||'|'||
        coalesce((SELECT count(*)::text FROM meetingintelligence.decisions d WHERE d.note_version_id=n.current_version_id),'0')||'|'||
        coalesce((SELECT count(*)::text FROM meetingintelligence.action_items a WHERE a.note_version_id=n.current_version_id),'0')||'|'||
        coalesce((SELECT count(*)::text FROM meetingintelligence.risks r WHERE r.note_version_id=n.current_version_id),'0')||'|'||
        coalesce((SELECT count(*)::text FROM meetingintelligence.proposals p WHERE p.note_version_id=n.current_version_id),'0')||'|'||
        coalesce((SELECT count(*)::text FROM meetingintelligence.open_questions o WHERE o.note_version_id=n.current_version_id),'0')
      FROM meetingintelligence.meeting_notes n
      LEFT JOIN meetingintelligence.meeting_note_versions v ON v.id=n.current_version_id
      WHERE n.meeting_occurrence_id='$meeting' LIMIT 1" 2>/dev/null || echo '')
    echo "  t=$i job=$row note(ver|rmr|conf|d|a|r|p|oq)=$note"
    case "$row" in SUCCEEDED*|DEAD*|FAILED*|CANCELLED*) echo "$row"; return 0 ;; esac
    sleep 30
  done
  echo "TIMEOUT|$job"
  return 1
}

dump_note() {
  local meeting="$1"
  local file="$2"
  local out="$OUT_DIR/${file%.vtt}.result.txt"
  local note vid
  note=$(psql -Atc "SELECT id FROM meetingintelligence.meeting_notes WHERE meeting_occurrence_id='$meeting' LIMIT 1")
  if [[ -z "${note:-}" ]]; then
    echo "NO_NOTE" > "$out"
    echo -e "${file}\t${meeting}\t\tNO_NOTE\t\t\t\t\t\t\t\t" >> "$SUMMARY"
    return 1
  fi
  vid=$(psql -Atc "SELECT current_version_id FROM meetingintelligence.meeting_notes WHERE id='$note'")
  {
    echo "MEETING=$meeting NOTE=$note VID=$vid FILE=$file"
    echo '==== META ===='
    psql -c "SELECT n.review_status, v.ai_confidence, left(v.executive_summary,400)
      FROM meetingintelligence.meeting_notes n
      JOIN meetingintelligence.meeting_note_versions v ON v.id=n.current_version_id WHERE n.id='$note';"
    echo '==== FLAGS ===='
    psql -c "SELECT code, left(coalesce(detail,''),120) FROM meetingintelligence.quality_flags WHERE note_version_id='$vid' ORDER BY 1,2;"
    echo '==== DECISIONS ===='
    psql -c "SELECT left(text,220) FROM meetingintelligence.decisions WHERE note_version_id='$vid';"
    echo '==== ACTIONS ===='
    psql -c "SELECT left(coalesce(owner,''),24), left(text,220) FROM meetingintelligence.action_items WHERE note_version_id='$vid';"
    echo '==== RISKS ===='
    psql -c "SELECT left(text,220) FROM meetingintelligence.risks WHERE note_version_id='$vid';"
    echo '==== PROPOSALS ===='
    psql -c "SELECT left(text,220) FROM meetingintelligence.proposals WHERE note_version_id='$vid';"
    echo '==== OPEN Q ===='
    psql -c "SELECT left(text,220) FROM meetingintelligence.open_questions WHERE note_version_id='$vid';"
  } | tee "$out"

  local meta flags_snip
  meta=$(psql -Atc "
    SELECT coalesce(n.review_status,'-')||'|'||coalesce(v.ai_confidence::text,'-')||'|'||
      coalesce((SELECT count(*)::text FROM meetingintelligence.decisions d WHERE d.note_version_id=n.current_version_id),'0')||'|'||
      coalesce((SELECT count(*)::text FROM meetingintelligence.action_items a WHERE a.note_version_id=n.current_version_id),'0')||'|'||
      coalesce((SELECT count(*)::text FROM meetingintelligence.risks r WHERE r.note_version_id=n.current_version_id),'0')||'|'||
      coalesce((SELECT count(*)::text FROM meetingintelligence.proposals p WHERE p.note_version_id=n.current_version_id),'0')||'|'||
      coalesce((SELECT count(*)::text FROM meetingintelligence.open_questions o WHERE o.note_version_id=n.current_version_id),'0')
    FROM meetingintelligence.meeting_notes n
    LEFT JOIN meetingintelligence.meeting_note_versions v ON v.id=n.current_version_id
    WHERE n.id='$note'")
  flags_snip=$(psql -Atc "SELECT string_agg(coalesce(detail, code::text), ';')
    FROM (
      SELECT code::text AS code, detail FROM meetingintelligence.quality_flags WHERE note_version_id='$vid'
      ORDER BY 1 LIMIT 12
    ) s")
  IFS='|' read -r review conf d a r p oq <<<"$meta"
  echo -e "${file}\t${meeting}\t${note}\tSUCCEEDED\t${review}\t${conf}\t${d}\t${a}\t${r}\t${p}\t${oq}\t${flags_snip}" >> "$SUMMARY"
}

run_one() {
  local file="$1"
  local minutes="$2"
  local vtt="$VTT_DIR/$file"
  [[ -f "$vtt" ]] || { echo "MISSING $vtt"; return 1; }

  echo
  echo "======== RUN $file (${minutes}m) ========"

  # Do not kill unrelated jobs mid-suite unless we are about to enqueue ours.
  psql -q -c "UPDATE modelmanagement.model_deployment SET status='HEALTHY', last_heartbeat_at=now();" >/dev/null

  local START END BODY code MEETING TR JOB
  START=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  END=$(date -u -d "+${minutes} minutes" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null \
    || date -u -v+"${minutes}"M +%Y-%m-%dT%H:%M:%SZ)

  BODY=$(python3 - <<PY
import json, uuid
gid = f"eval-suite-{uuid.uuid4()}"
print(json.dumps({
  "businessContextId": "$CTX",
  "title": "[EVAL] realistic $file",
  "meetingType": "STANDALONE",
  "scheduledStartAt": "$START",
  "scheduledEndAt": "$END",
  "processingPriority": "HIGH",
  "graphEventImmutableId": gid,
  "icalUid": gid,
  "originalStartAt": "$START",
  "participants": [
    {"entraUserId": "8d69a8bc-0165-4fd3-ba31-541933b5d1f0", "displayName": "Murat Sancar",
     "email": "muratsancar@nanobase.ai", "participantType": "ORGANIZER", "external": False},
    {"entraUserId": "11111111-1111-1111-1111-111111111101", "displayName": "Eval A",
     "email": "eval.a@nanobase.ai", "participantType": "REQUIRED", "external": False},
    {"entraUserId": "11111111-1111-1111-1111-111111111102", "displayName": "Eval B",
     "email": "eval.b@nanobase.ai", "participantType": "REQUIRED", "external": False},
  ]
}))
PY
)

  code=$(curl -s -o /tmp/meet.json -w "%{http_code}" -X POST "$BASE/api/v1/meetings" \
    "${AUTH_HDR[@]}" -H "Content-Type: application/json" -d "$BODY")
  echo "create_meeting=$code"
  MEETING=$(python3 -c 'import json; print(json.load(open("/tmp/meet.json")).get("id",""))')
  [[ -n "$MEETING" ]] || { cat /tmp/meet.json; return 1; }

  code=$(curl -s -o /tmp/up.json -w "%{http_code}" -X POST "$BASE/api/v1/transcripts/upload" \
    "${AUTH_HDR[@]}" \
    -F "meetingOccurrenceId=$MEETING" \
    -F "language=tr" \
    -F "file=@${vtt};type=text/vtt")
  echo "upload=$code"
  TR=$(python3 -c 'import json; d=json.load(open("/tmp/up.json")); print(d.get("id") or d.get("transcriptId",""))')
  [[ -n "$TR" ]] || { cat /tmp/up.json; return 1; }

  code=$(curl -s -o /tmp/reparse.json -w "%{http_code}" -X POST \
    "${AUTH_HDR[@]}" "$BASE/api/v1/transcripts/$TR/reparse")
  echo "reparse=$code"

  sleep 2
  # Always enqueue a fresh job for THIS meeting — never reuse an old SUCCEEDED job
  # (upload may 409 and return a transcript already processed on another occurrence).
  JOB=$(python3 -c 'import uuid; print(uuid.uuid4())')
  CORR=$(python3 -c 'import uuid; print(uuid.uuid4())')
  IDEM="suite-${file}-$(date -u +%Y%m%d%H%M%S)-$(python3 -c 'import uuid; print(uuid.uuid4().hex[:8])')"
  psql -q -c "UPDATE aiprocessing.ai_jobs SET status='DEAD', completed_at=COALESCE(completed_at, now()), version=version+1
    WHERE status IN ('QUEUED','RUNNING');" >/dev/null || true
  psql -v ON_ERROR_STOP=1 -c "
    INSERT INTO aiprocessing.ai_jobs (
      id, tenant_id, meeting_occurrence_id, transcript_id, task_type, priority, status,
      requested_capability, selected_model_id, selected_deployment_id, selected_route_reason,
      prompt_version, schema_version, queued_at, deadline_at, correlation_id, language,
      context_size, fallback_permitted, attempt_count, version, stage, idempotency_key
    ) VALUES (
      '$JOB', '$TENANT', '$MEETING', '$TR', 'CHUNK_EXTRACTION', 'HIGH', 'QUEUED',
      'DECISION_EXTRACTION', '$MODEL', '$DEP', 'realistic-suite',
      'pv-meeting-chunk-extraction-v2', 'extraction-output.v1', now(), now() + interval '48 hours', '$CORR', 'tr',
      16384, true, 0, 0, 'LEGACY', '$IDEM'
    );"
  echo "MEETING=$MEETING TR=$TR JOB=$JOB" | tee "$OUT_DIR/${file%.vtt}.ids"

  # Wall-clock budget: ~12x meeting duration, floor 12h, ceiling 72h (poll every 30s).
  local max_loops=$(( minutes * 24 ))
  [[ $max_loops -lt 1440 ]] && max_loops=1440   # >= 12h
  [[ $max_loops -gt 8640 ]] && max_loops=8640   # <= 72h
  local status
  status=$(wait_job "$JOB" "$MEETING" "$max_loops")
  echo "final_status=$status"
  dump_note "$MEETING" "$file" || true
}

for entry in "${FILES[@]}"; do
  f="${entry%%:*}"
  m="${entry##*:}"
  if [[ -n "$ONLY" && "$f" != "$ONLY" ]]; then
    continue
  fi
  run_one "$f" "$m" || echo "FAILED $f" | tee -a "$OUT_DIR/errors.log"
done

echo
echo "======== SUMMARY ========"
column -t -s $'\t' "$SUMMARY" 2>/dev/null || cat "$SUMMARY"
echo "Wrote $SUMMARY"
