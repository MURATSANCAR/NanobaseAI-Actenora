#!/usr/bin/env bash
set -euo pipefail
REMOTE_ROOT=/data/nanobaseai/actenora/eval/standup-v0.1/OQ_CUE_SEED
LOCAL_ROOT=/Users/msancar/Documents/GitHub/NanobaseAI-Actenora/artifacts/easymeeting-quality/OQ_CUE_SEED
SCORE=/Users/msancar/Documents/GitHub/NanobaseAI-Actenora/scripts/score-easymeeting-gold.py
LOG="$LOCAL_ROOT/watch.log"
mkdir -p "$LOCAL_ROOT"
echo "watch start $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a "$LOG"

for i in $(seq 1 240); do
  done1=$(ssh -o ConnectTimeout=20 nanobase "test -f $REMOTE_ROOT/run_01/finished_at.txt && echo 1 || echo 0" || echo ERR)
  done2=$(ssh -o ConnectTimeout=20 nanobase "test -f $REMOTE_ROOT/run_02/finished_at.txt && echo 1 || echo 0" || echo ERR)
  camp=$(ssh -o ConnectTimeout=20 nanobase "tail -1 $REMOTE_ROOT/campaign.log 2>/dev/null || true" || true)
  echo "$(date -u +%H:%M:%S) poll=$i done1=$done1 done2=$done2 camp=$camp" | tee -a "$LOG"
  if [[ "$done1" == "1" && "$done2" == "1" ]]; then
    for run in 01 02; do
      mkdir -p "$LOCAL_ROOT/run_$run"
      scp -q "nanobase:$REMOTE_ROOT/run_$run/final.note.json" "$LOCAL_ROOT/run_$run/" || true
      scp -q "nanobase:$REMOTE_ROOT/run_$run/suite.log" "$LOCAL_ROOT/run_$run/" || true
      scp -q "nanobase:$REMOTE_ROOT/run_$run/finished_at.txt" "$LOCAL_ROOT/run_$run/" || true
      python3 "$SCORE" --note "$LOCAL_ROOT/run_$run/final.note.json" --out "$LOCAL_ROOT/run_$run/score.json" \
        >"$LOCAL_ROOT/run_$run/score.log" 2>&1
    done
    python3 - <<'PY' | tee -a "$LOG"
import json
from pathlib import Path
root = Path("/Users/msancar/Documents/GitHub/NanobaseAI-Actenora/artifacts/easymeeting-quality/OQ_CUE_SEED")
print("=== OQ_CUE_SEED RESULTS ===")
for run in ["run_01", "run_02"]:
    sc = json.loads((root / run / "score.json").read_text())
    note = json.loads((root / run / "final.note.json").read_text())
    flags = note.get("qualityFlags") or []
    print(
        run,
        "oq=", (sc.get("openQuestion") or {}).get("recall"),
        "action=", (sc.get("actionItem") or {}).get("recall"),
        "gate=", sc.get("criticalGatePassed"),
        "fact=", (sc.get("importantFact") or {}).get("recall"),
        "compound=", sc.get("compoundSplitAccuracy"),
        "fail=", sc.get("failureReasonCodes"),
        "oq_count=", len(note.get("openQuestions") or []),
        "cue_flag=", any("OPEN_QUESTION_CUE" in str(f) for f in flags),
    )
    for a in note.get("actionItems") or []:
        t = (a.get("text") or "").lower()
        if "utf" in t or "paralel" in t or "refresh" in t:
            print(" ", a.get("owner"), (a.get("text") or "")[:120])
PY
    echo "AGENT_LOOP_TICK_oqcue done"
    exit 0
  fi
  sleep 120
done
echo "TIMEOUT $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a "$LOG"
exit 1
