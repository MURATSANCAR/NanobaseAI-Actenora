#!/usr/bin/env bash
set -euo pipefail
REMOTE=/data/nanobaseai/actenora/eval/real-teams
LOCAL=/Users/msancar/Documents/GitHub/NanobaseAI-Actenora/artifacts/eval/real-teams
LOG="$LOCAL/pull.log"
echo "pull-watch start $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a "$LOG"
for i in $(seq 1 300); do
  camp=$(ssh -o ConnectTimeout=20 nanobase "tail -3 $REMOTE/campaign.log 2>/dev/null || true" || echo ERR)
  echo "$(date -u +%H:%M:%S) $camp" | tee -a "$LOG"
  yz_ok=0; bim_ok=0
  ssh nanobase "test -f $REMOTE/2026-08-05_yapay-zeka-gorusmesi/run_01/quality-eval-pack.json" && yz_ok=1 || true
  ssh nanobase "test -f $REMOTE/2026-08-05_bim-tanisma/run_01/quality-eval-pack.json" && bim_ok=1 || true
  # also accept MISSING pack if final.note exists after SUCCEEDED - but prefer pack
  if [[ "$yz_ok" == "1" ]]; then
    mkdir -p "$LOCAL/2026-08-05_yapay-zeka-gorusmesi/run_01" "$LOCAL/2026-08-05_yapay-zeka-gorusmesi/source"
    scp -q nanobase:$REMOTE/2026-08-05_yapay-zeka-gorusmesi/run_01/* "$LOCAL/2026-08-05_yapay-zeka-gorusmesi/run_01/" 2>>"$LOG" || true
    scp -q nanobase:$REMOTE/2026-08-05_yapay-zeka-gorusmesi/source/* "$LOCAL/2026-08-05_yapay-zeka-gorusmesi/source/" 2>>"$LOG" || true
    echo "YZ_PULLED" | tee -a "$LOG"
  fi
  if [[ "$bim_ok" == "1" ]]; then
    mkdir -p "$LOCAL/2026-08-05_bim-tanisma/run_01" "$LOCAL/2026-08-05_bim-tanisma/source"
    scp -q nanobase:$REMOTE/2026-08-05_bim-tanisma/run_01/* "$LOCAL/2026-08-05_bim-tanisma/run_01/" 2>>"$LOG" || true
    scp -q nanobase:$REMOTE/2026-08-05_bim-tanisma/source/* "$LOCAL/2026-08-05_bim-tanisma/source/" 2>>"$LOG" || true
    echo "BIM_PULLED" | tee -a "$LOG"
  fi
  if ssh nanobase "grep -q '==== COMPLETE' $REMOTE/campaign.log 2>/dev/null"; then
    # final sync
    for slug in 2026-08-05_yapay-zeka-gorusmesi 2026-08-05_bim-tanisma; do
      mkdir -p "$LOCAL/$slug/run_01" "$LOCAL/$slug/source"
      scp -q nanobase:$REMOTE/$slug/run_01/* "$LOCAL/$slug/run_01/" 2>>"$LOG" || true
      scp -q nanobase:$REMOTE/$slug/source/* "$LOCAL/$slug/source/" 2>>"$LOG" || true
    done
    echo "AGENT_LOOP_TICK_realteams {\"prompt\":\"Real Teams exports complete. Verify both folders under artifacts/eval/real-teams have quality-eval-pack.json, ids.json, env.snapshot.txt, transcript.vtt. Report checklist readiness.\"}"
    exit 0
  fi
  sleep 90
done
echo TIMEOUT | tee -a "$LOG"
exit 1
