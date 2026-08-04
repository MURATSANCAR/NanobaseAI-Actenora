#!/usr/bin/env python3
"""Build cue-27/cue-51 traces from lineage-rerun artifacts (deterministic)."""
from __future__ import annotations

import argparse
import json
import re
import unicodedata
from collections import Counter
from pathlib import Path


def norm(s: str) -> str:
    s = (s or "").lower()
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    for a, b in [("ı", "i"), ("ğ", "g"), ("ü", "u"), ("ş", "s"), ("ö", "o"), ("ç", "c")]:
        s = s.replace(a, b)
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9\s]", " ", s)).strip()


def load(p: Path):
    if not p.exists():
        return None
    try:
        return json.loads(p.read_text())
    except Exception:
        return None


def note_has(note, needles, owner=None):
    for a in note.get("actionItems") or []:
        t = norm(a.get("text", ""))
        if all(n in t for n in needles):
            if owner and not (a.get("owner") or "").lower().startswith(owner.lower()[:3]):
                continue
            return True, a
    return False, None


def analyze_run(run_dir: Path, cue: int) -> dict:
    note = load(run_dir / "final.note.json") or {}
    lin = load(run_dir / "lineage.json") or {}
    events = lin.get("events") or []
    stages = Counter(e.get("stage") for e in events)
    ops = Counter(e.get("operation") for e in events)
    splits = [e for e in events if e.get("operation") == "SPLIT" or e.get("reasonCode") == "ACTION_COMPOUND_SPLIT"]
    drops = [e for e in events if e.get("operation") == "DROP"]

    if cue == 51:
        can_ok, can_a = note_has(note, ["utf"], "Can")
        if not can_ok:
            can_ok, can_a = note_has(note, ["baslik", "duzelt"], "Can")
        burak_ok, burak_a = note_has(note, ["outlook"], "Burak")
        if not burak_ok:
            burak_ok, burak_a = note_has(note, ["apple", "mail"], "Burak")
        selin_ok, selin_a = False, None
    else:
        selin_ok, selin_a = note_has(note, ["duzelt"], "Selin")
        can_ok, can_a = note_has(note, ["correlation"], "Can")
        burak_ok, burak_a = False, None

    answers = {
        "1_chunk": "NOT_OBSERVABLE",
        "2_gate": "NOT_OBSERVABLE",
        "3_raw_action_count": "NOT_OBSERVABLE",
        "4_raw_action_texts": "NOT_OBSERVABLE",
        "5_can_clause_raw": "NOT_OBSERVABLE",
        "6_burak_or_selin_clause_raw": "NOT_OBSERVABLE",
        "7_compound_candidate": "NOT_OBSERVABLE",
        "8_decomposer_child_count": len(splits) if splits else "NOT_OBSERVABLE",
        "9_can_owner_bound": bool(can_a and (can_a.get("owner") or "").startswith("Can")) if can_a else False,
        "10_other_owner_bound": (
            bool(burak_a and (burak_a.get("owner") or "").startswith("Burak")) if cue == 51 and burak_a
            else bool(selin_a and (selin_a.get("owner") or "").startswith("Selin")) if cue == 27 and selin_a
            else False
        ),
        "11_date_binding": "NOT_OBSERVABLE" if not events else "SEE_LINEAGE_EVENTS",
        "12_title_backfill": "NOT_OBSERVABLE",
        "13_backfill_cue49": "NOT_OBSERVABLE",
        "14_dedup_drop": any(e.get("reasonCode") == "ACTION_DEDUPLICATED" for e in drops),
        "15_cross_type_drop": any("CROSS_TYPE" in str(e.get("reasonCode")) for e in drops),
        "16_validated_bundle": "NOT_OBSERVABLE",
        "17_final_note_mapped": {
            "can_or_first": can_ok,
            "second": burak_ok if cue == 51 else selin_ok,
        },
    }

    reason = []
    if cue == 51:
        if burak_ok and not can_ok:
            if splits:
                reason.append("COMPOUND_DECOMPOSER_PARTIAL_SPLIT")
            elif events:
                reason.append("LLM_MISSED_FIRST_CLAUSE")
            else:
                reason.append("NOT_OBSERVABLE")
                reason.append("UNCLASSIFIED_MISS")
        elif can_ok and burak_ok:
            reason.append("PASS")
        else:
            reason.append("UNCLASSIFIED_MISS")
    else:
        if can_ok and not selin_ok:
            reason.append("COMPOUND_DECOMPOSER_PARTIAL_SPLIT" if splits else "UNCLASSIFIED_MISS")
        elif can_ok and selin_ok:
            reason.append("PASS")
        else:
            reason.append("UNCLASSIFIED_MISS")

    return {
        "runDir": str(run_dir),
        "lineageEventCount": len(events),
        "stagesObserved": dict(stages),
        "operationsObserved": dict(ops),
        "splitEvents": len(splits),
        "dropEvents": len(drops),
        "finalNote": {
            "canPresent": can_ok,
            "secondPresent": burak_ok if cue == 51 else selin_ok,
            "canAction": can_a,
            "secondAction": burak_a if cue == 51 else selin_a,
        },
        "traceAnswers": answers,
        "reasonCodes": reason,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tree-root", type=Path, required=True)
    args = ap.parse_args()
    root = args.tree_root
    for cue, name in ((27, "cue-27-trace"), (51, "cue-51-trace")):
        runs = []
        for tree in ("B_LINEAGE_f9c699f", "A_LINEAGE_472172a"):
            for run in ["smoke_01"] + [f"run_{i:02d}" for i in range(1, 6)]:
                d = root / tree / run
                if not (d / "final.note.json").exists():
                    continue
                r = analyze_run(d, cue)
                r["tree"] = tree
                r["run"] = run
                runs.append(r)
        out = {"cue": cue, "runs": runs}
        (root / f"{name}.json").write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n")
        lines = [f"# Cue {cue} trace", ""]
        for r in runs:
            lines.append(f"## {r['tree']} / {r['run']}")
            lines.append(f"- lineage events: {r['lineageEventCount']}")
            lines.append(f"- reasonCodes: {r['reasonCodes']}")
            lines.append(f"- final: {r['finalNote']}")
            lines.append("")
        (root / f"{name}.md").write_text("\n".join(lines) + "\n")
        print("wrote", root / f"{name}.json")

    # aggregate compound root cause
    c27 = json.loads((root / "cue-27-trace.json").read_text())
    c51 = json.loads((root / "cue-51-trace.json").read_text())

    def agg(trace, cue):
        fails_b = fails_a = 0
        by_reason = Counter()
        for r in trace["runs"]:
            if r["run"].startswith("smoke"):
                continue
            codes = [c for c in r["reasonCodes"] if c != "PASS"]
            if not codes:
                continue
            if r["tree"].startswith("B_"):
                fails_b += 1
            else:
                fails_a += 1
            for c in codes:
                by_reason[c] += 1
        confirmed = [k for k, v in by_reason.most_common() if k not in ("NOT_OBSERVABLE", "UNCLASSIFIED_MISS")]
        return {
            "controlFailureCount": fails_b,
            "candidateFailureCount": fails_a,
            "failureCountsByStage": {},
            "failureCountsByReasonCode": dict(by_reason),
            "confirmedRootCauses": confirmed[:5],
        }

    a27, a51 = agg(c27, 27), agg(c51, 51)
    systemic = "NOT_OBSERVABLE"
    if a51["failureCountsByReasonCode"].get("COMPOUND_DECOMPOSER_PARTIAL_SPLIT"):
        systemic = "COMPOUND_DECOMPOSER_PARTIAL_SPLIT"
    elif a51["failureCountsByReasonCode"].get("LLM_MISSED_FIRST_CLAUSE"):
        systemic = "LLM_MISSED_FIRST_CLAUSE"
    summary = {
        "cue27": a27,
        "cue51": a51,
        "candidateImprovements": [],
        "candidateRegressions": [],
        "systemicRootCause": systemic,
        "recommendedFix": (
            "After lineage confirms stage: if COMPOUND_DECOMPOSER_PARTIAL_SPLIT or ACTION_TITLE_CONTEXT_LOSS on Can UTF-8 clause, "
            "fix compound split/title-backfill for cue-51 style clauses only — do not change gate/cross-type yet."
            if systemic != "NOT_OBSERVABLE"
            else "Insufficient stage evidence; do not change extraction until lineage events prove drop stage."
        ),
        "recommendedFixScope": ["action-compound-decomposition", "action-title-backfill"] if systemic != "NOT_OBSERVABLE" else [],
        "notRecommendedYet": [
            "typed relation resolver",
            "semantic speech-act classifier",
            "tokenizer replacement",
        ],
    }
    (root / "compound-action-root-cause.json").write_text(json.dumps(summary, indent=2) + "\n")
    (root / "compound-action-root-cause.md").write_text(
        "# Compound action root cause\n\n"
        + f"Systemic: `{systemic}`\n\n"
        + f"Recommended: {summary['recommendedFix']}\n"
    )
    print("wrote compound-action-root-cause.json")


if __name__ == "__main__":
    main()
