#!/usr/bin/env bash
# Wait for C campaign screen to finish, then score + write final artifacts on nanobase.
# Safe to run detached; does not depend on Cursor being open.
set -euo pipefail

EVAL_ROOT="${EVAL_ROOT:-/data/nanobaseai/actenora/eval/standup-ba/phase1b/C_CANDIDATE_ACTION_TITLE_CONTEXT}"
TREE_DIR="$EVAL_ROOT/C_CANDIDATE_ACTION_TITLE_CONTEXT"
STATUS="$EVAL_ROOT/campaign.STATUS.txt"
LOG="$EVAL_ROOT/finalize.log"
SCRIPTS_DIR="${SCRIPTS_DIR:-/data/nanobaseai/actenora/eval/standup-ba}"
GOLD="${GOLD:-$SCRIPTS_DIR/01_15dk_daily_standup.gold.v1.json}"
SCORER="${SCORER:-$SCRIPTS_DIR/score-easymeeting-gold.py}"
B_ROOT="${B_ROOT:-/data/nanobaseai/actenora/eval/standup-ba/artifacts/B_HEAD_f9c699f}"
A_ROOT="${A_ROOT:-/data/nanobaseai/actenora/eval/standup-ba/artifacts/A_CAND_472172a}"
# Fallback local mirrored paths if present on server
[[ -d "$B_ROOT" ]] || B_ROOT="/data/nanobaseai/actenora/eval/standup-ba/phase1b/lineage-rerun/B_LINEAGE_f9c699f"
[[ -d "$A_ROOT" ]] || A_ROOT="/data/nanobaseai/actenora/eval/standup-ba/phase1b/lineage-rerun/A_LINEAGE_472172a"

mkdir -p "$EVAL_ROOT"
exec >>"$LOG" 2>&1

ts() { date -u +%Y-%m-%dT%H:%M:%SZ; }
echo "==== finalize watcher start $(ts) ===="

# Wait until campaign STATUS says COMPLETE (or FAILED) and all 5 final notes exist
for i in $(seq 1 360); do  # up to ~6h @ 60s
  st="$(cat "$STATUS" 2>/dev/null || true)"
  ok=0
  for r in 01 02 03 04 05; do
    [[ -f "$TREE_DIR/run_$r/final-note.json" || -f "$TREE_DIR/run_$r/final.note.json" ]] && ok=$((ok+1))
  done
  echo "$(ts) status=$st runs_ready=$ok/5"
  if [[ "$st" == COMPLETE* && "$ok" -eq 5 ]]; then
    break
  fi
  if [[ "$st" == FAILED* || "$st" == TRAP* ]]; then
    echo "campaign failed/trap: $st"
    break
  fi
  # If screen gone and we have 5 runs, proceed
  if ! screen -ls 2>/dev/null | grep -q 'actenora-c-candidate'; then
    if [[ "$ok" -eq 5 ]]; then
      echo "campaign screen gone with 5/5 artifacts"
      break
    fi
  fi
  sleep 60
done

ok=0
for r in 01 02 03 04 05; do
  [[ -f "$TREE_DIR/run_$r/final-note.json" || -f "$TREE_DIR/run_$r/final.note.json" ]] && ok=$((ok+1))
done
if [[ "$ok" -lt 5 ]]; then
  echo "FINALIZE_BLOCKED runs_ready=$ok/5 status=$(cat "$STATUS" 2>/dev/null || true)" | tee "$EVAL_ROOT/FINALIZE.STATUS.txt"
  exit 2
fi

echo "==== scoring $(ts) ===="
for r in 01 02 03 04 05; do
  d="$TREE_DIR/run_$r"
  note="$d/final-note.json"
  [[ -f "$note" ]] || note="$d/final.note.json"
  python3 "$SCORER" --note "$note" --gold "$GOLD" --out "$d/score.json"
done

python3 - <<'PY'
import json, statistics, re
from pathlib import Path
from collections import Counter

eval_root = Path("/data/nanobaseai/actenora/eval/standup-ba/phase1b/C_CANDIDATE_ACTION_TITLE_CONTEXT")
tree = eval_root / "C_CANDIDATE_ACTION_TITLE_CONTEXT"

def load_score(p):
    return json.loads(p.read_text())

runs = []
for r in ["01","02","03","04","05"]:
    d = tree / f"run_{r}"
    score = load_score(d / "score.json")
    note_path = d / "final-note.json"
    if not note_path.exists():
        note_path = d / "final.note.json"
    note = json.loads(note_path.read_text())
    lineage_raw = (d / "lineage.json").read_text() if (d / "lineage.json").exists() else "{}"
    try:
        lineage = json.loads(lineage_raw)
    except Exception:
        lineage = {}
    events = lineage.get("events") or []
    if isinstance(lineage, list):
        events = lineage
    backfill_upd = sum(1 for e in events if str(e.get("stage",""))=="ACTION_TITLE_BACKFILL" and str(e.get("operation",""))=="UPDATE")
    backfill_amb = sum(1 for e in events if str(e.get("reasonCode",""))=="ACTION_TITLE_CONTEXT_AMBIGUOUS")
    actions = note.get("actionItems") or []
    texts = " | ".join((a.get("text") or "") for a in actions).lower()
    a06 = any(("utf" in (a.get("text") or "").lower() or "gönderim" in (a.get("text") or "").lower() or "gonderim" in (a.get("text") or "").lower())
              and "can" in ((a.get("owner") or "")+(a.get("text") or "")).lower() for a in actions)
    # Prefer gold score item results if present
    item = {i.get("id"): i for i in (score.get("items") or score.get("itemResults") or [])}
    def item_pass(gid):
        it = item.get(gid) or {}
        return bool(it.get("pass") or it.get("matched") or it.get("status")=="PASS")
    cue51 = item_pass("A-06") and item_pass("A-07")
    cue27 = item_pass("A-03") and item_pass("A-04")
    rt = {}
    if (d/"runtime-metrics.json").exists():
        try: rt = json.loads((d/"runtime-metrics.json").read_text())
        except Exception: rt = {}
    runs.append({
        "run": r,
        "score": score,
        "criticalGate": score.get("criticalGatePass") or score.get("criticalGate", {}).get("pass") or score.get("gates", {}).get("criticalGatePass"),
        "actionRecall": score.get("actionRecall") or score.get("metrics", {}).get("actionRecall"),
        "overall": score.get("overallScore") or score.get("score") or score.get("metrics", {}).get("overallScore"),
        "a06": item_pass("A-06") or a06,
        "a07": item_pass("A-07"),
        "a03": item_pass("A-03"),
        "a04": item_pass("A-04"),
        "cue51": cue51 or (item_pass("A-06") and item_pass("A-07")),
        "cue27": cue27 or (item_pass("A-03") and item_pass("A-04")),
        "backfillUpdate": backfill_upd,
        "backfillAmbiguous": backfill_amb,
        "durationMs": rt.get("durationMs"),
        "ownerErrors": score.get("ownerHallucinationCount", score.get("metrics", {}).get("ownerHallucinationCount", 0)),
        "dateErrors": score.get("dateHallucinationCount", score.get("metrics", {}).get("dateHallucinationCount", 0)),
        "hallucinatedDecisions": score.get("hallucinatedDecisionCount", score.get("metrics", {}).get("hallucinatedDecisionCount", 0)),
        "statusQuoFp": score.get("statusQuoDecisionFalsePositiveCount", score.get("metrics", {}).get("statusQuoDecisionFalsePositiveCount", 0)),
        "metaLeak": score.get("closingMetaLeakageCount", score.get("metrics", {}).get("closingMetaLeakageCount", 0)),
        "actionsPreview": [(a.get("owner"), a.get("text")) for a in actions],
    })

def rate(key):
    return sum(1 for r in runs if r[key]) / len(runs)

durs = [r["durationMs"] for r in runs if isinstance(r.get("durationMs"), (int, float))]
stability = {
    "candidate": "C_CANDIDATE_ACTION_TITLE_CONTEXT",
    "runs": len(runs),
    "criticalGatePassRate": rate("criticalGate") if any(r["criticalGate"] is not None for r in runs) else None,
    "a06PassRate": rate("a06"),
    "a07PassRate": rate("a07"),
    "a03PassRate": rate("a03"),
    "a04PassRate": rate("a04"),
    "cue51PassRate": rate("cue51"),
    "cue27PassRate": rate("cue27"),
    "actionRecall": [r["actionRecall"] for r in runs],
    "overallScore": [r["overall"] for r in runs],
    "backfillUpdateCounts": [r["backfillUpdate"] for r in runs],
    "ambiguousNoOpCounts": [r["backfillAmbiguous"] for r in runs],
    "durationMs": durs,
    "p50DurationMs": statistics.median(durs) if durs else None,
    "p95DurationMs": sorted(durs)[max(0, int(round(0.95*(len(durs)-1))))] if durs else None,
    "runDetails": runs,
}
(tree/"stability.json").write_text(json.dumps(stability, indent=2, ensure_ascii=False)+"\n")

# Hard gate
gate_ok = (
    stability["a06PassRate"] == 1.0
    and stability["a07PassRate"] == 1.0
    and stability["a03PassRate"] == 1.0
    and stability["a04PassRate"] == 1.0
    and stability["cue51PassRate"] == 1.0
    and stability["cue27PassRate"] == 1.0
    and all((r.get("hallucinatedDecisions") or 0) == 0 for r in runs)
    and all((r.get("statusQuoFp") or 0) == 0 for r in runs)
    and all((r.get("ownerErrors") or 0) == 0 for r in runs)
    and all((r.get("dateErrors") or 0) == 0 for r in runs)
    and all((r.get("metaLeak") or 0) == 0 for r in runs)
)
# action recall threshold if numeric
ar = [r["actionRecall"] for r in runs if isinstance(r["actionRecall"], (int, float))]
if ar:
    gate_ok = gate_ok and min(ar) >= 0.90
cg = [r["criticalGate"] for r in runs if r["criticalGate"] is not None]
if cg:
    gate_ok = gate_ok and all(bool(x) for x in cg)

decision = "C_CANDIDATE_QUALITY_GATE_PASSED" if gate_ok else "C_CANDIDATE_QUALITY_GATE_FAILED"
reasons = []
if stability["cue51PassRate"] < 1.0: reasons.append("CUE51_NOT_5_OF_5")
if stability["a06PassRate"] < 1.0: reasons.append("A06_NOT_5_OF_5")
if stability["cue27PassRate"] < 1.0: reasons.append("CUE27_NOT_5_OF_5")
if ar and min(ar) < 0.90: reasons.append("ACTION_RECALL_BELOW_0_90")
if not gate_ok and not reasons: reasons.append("HARD_GATE_METRIC_MISS")

# Cue traces from first run with lineage
def cue_trace(run_id):
    d = tree / f"run_{run_id}"
    note = json.loads((d/"final-note.json" if (d/"final-note.json").exists() else d/"final.note.json").read_text())
    lineage = {}
    try:
        lineage = json.loads((d/"lineage.json").read_text())
    except Exception:
        pass
    events = lineage.get("events") or (lineage if isinstance(lineage, list) else [])
    backfills = [e for e in events if str(e.get("stage")) == "ACTION_TITLE_BACKFILL"]
    return {
        "run": run_id,
        "actionItems": note.get("actionItems"),
        "titleBackfillEvents": backfills[:20],
    }

(tree/"cue-51-trace.json").write_text(json.dumps(cue_trace("01"), indent=2, ensure_ascii=False)+"\n")
(tree/"cue-27-trace.json").write_text(json.dumps(cue_trace("01"), indent=2, ensure_ascii=False)+"\n")

comparison = {
    "metric": [
        "actionRecall", "overallScore", "criticalGatePassRate", "a06PassRate", "cue51PassRate",
        "cue27PassRate", "ownerDateErrors", "statusQuoFp", "hallucinatedDecisions", "metaLeakage",
        "p50Duration", "p95Duration", "backfillTriggerCount", "ambiguousNoOpCount"
    ],
    "C": {
        "actionRecall": stability["actionRecall"],
        "overallScore": stability["overallScore"],
        "criticalGatePassRate": stability["criticalGatePassRate"],
        "a06PassRate": stability["a06PassRate"],
        "cue51PassRate": stability["cue51PassRate"],
        "cue27PassRate": stability["cue27PassRate"],
        "backfillTriggerCount": stability["backfillUpdateCounts"],
        "ambiguousNoOpCount": stability["ambiguousNoOpCounts"],
        "p50Duration": stability["p50DurationMs"],
        "p95Duration": stability["p95DurationMs"],
    },
    "note": "B/A numeric baselines should be merged from prior campaign artifacts when mirrored on host.",
}
(tree/"B_vs_A_vs_C_comparison.json").write_text(json.dumps(comparison, indent=2, ensure_ascii=False)+"\n")

report = f"""# C_CANDIDATE_ACTION_TITLE_CONTEXT — final report

Generated: finalize watcher on nanobase
Decision: **{decision}**
Reason codes: {', '.join(reasons) if reasons else 'NONE'}

## Hard gates
- A-06 pass rate: {stability['a06PassRate']}
- A-07 pass rate: {stability['a07PassRate']}
- A-03 pass rate: {stability['a03PassRate']}
- A-04 pass rate: {stability['a04PassRate']}
- Cue 51 pass rate: {stability['cue51PassRate']}
- Cue 27 pass rate: {stability['cue27PassRate']}
- Action recall: {stability['actionRecall']}
- Critical gate: {stability['criticalGatePassRate']}
- Backfill UPDATE counts: {stability['backfillUpdateCounts']}

## Production impact
- Production deploy: NO
- Prompt/gate/splitter/cross-type/finalization unchanged
- Only low-specificity action title context backfill added
- Ambiguous context => NO_UPDATE

## Runtime
- P50 ms: {stability['p50DurationMs']}
- P95 ms: {stability['p95DurationMs']}
"""
(tree/"final-report.md").write_text(report)
(eval_root/"FINALIZE.STATUS.txt").write_text(f"{decision} at={__import__('datetime').datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%SZ')}\n")
print(decision)
print("wrote", tree/"stability.json")
PY

echo "==== finalize done $(ts) ===="
cat "$EVAL_ROOT/FINALIZE.STATUS.txt" || true
