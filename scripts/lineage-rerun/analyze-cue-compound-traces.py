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


def text_of(e: dict, side: str = "after") -> str:
    snap = e.get(side) or {}
    if isinstance(snap, dict):
        return snap.get("text") or ""
    return ""


def note_has(note, needles, owner=None):
    for a in note.get("actionItems") or []:
        t = norm(a.get("text", ""))
        if all(n in t for n in needles):
            if owner and not (a.get("owner") or "").lower().startswith(owner.lower()[:3]):
                continue
            return True, a
    return False, None


def lineage_actions(events):
    """Action-ish lineage rows with text."""
    out = []
    for e in events:
        if e.get("candidateType") not in (None, "ACTION_ITEM", "ACTION"):
            # still include compound stages even if type missing
            if e.get("stage") not in {
                "ACTION_COMPOUND_DECOMPOSITION",
                "ACTION_TITLE_BACKFILL",
                "ACTION_DEDUPLICATION",
                "ACTION_RELATIVE_DATE_BINDING",
                "ACTION_CLAUSE_BINDING",
                "ACTION_PREFIX_NORMALIZATION",
                "CROSS_TYPE_RESOLUTION",
            }:
                continue
        t = text_of(e, "after") or text_of(e, "before")
        out.append((e, t, norm(t)))
    return out


def analyze_run(run_dir: Path, cue: int) -> dict:
    note = load(run_dir / "final.note.json") or {}
    lin = load(run_dir / "lineage.json") or {}
    app = load(run_dir / "action-post-processing.json") or {}
    events = lin.get("events") or []
    stages = Counter(e.get("stage") for e in events)
    ops = Counter(e.get("operation") for e in events)
    splits = [
        e
        for e in events
        if e.get("operation") == "SPLIT" or e.get("reasonCode") == "ACTION_COMPOUND_SPLIT"
    ]
    not_splits = [e for e in events if e.get("reasonCode") == "ACTION_COMPOUND_NOT_SPLIT"]
    drops = [e for e in events if e.get("operation") == "DROP"]
    backfills = [e for e in events if e.get("stage") == "ACTION_TITLE_BACKFILL"]
    rows = lineage_actions(events)

    if cue == 51:
        can_ok, can_a = note_has(note, ["utf"], "Can")
        if not can_ok:
            can_ok, can_a = note_has(note, ["baslik", "duzelt"], "Can")
        can_utf_ok, can_utf_a = note_has(note, ["utf"], "Can")
        burak_ok, burak_a = note_has(note, ["outlook"], "Burak")
        if not burak_ok:
            burak_ok, burak_a = note_has(note, ["apple", "mail"], "Burak")
        selin_ok, selin_a = False, None
    else:
        selin_ok, selin_a = note_has(note, ["duzelt"], "Selin")
        can_ok, can_a = note_has(note, ["correlation"], "Can")
        can_utf_ok, can_utf_a = False, None
        burak_ok, burak_a = False, None

    # Lineage clause presence
    def owner_of(e: dict) -> str:
        return str(((e.get("after") or {}).get("owner") or ((e.get("before") or {}).get("owner") or "")))

    can_clause_lin = any(
        ("can" in nt or owner_of(e).lower().startswith("can"))
        and any(k in nt for k in ("baslik", "utf", "duzelt", "correlation"))
        for e, t, nt in rows
    )
    burak_clause_lin = any(
        ("burak" in nt or owner_of(e).lower().startswith("burak"))
        and any(k in nt for k in ("outlook", "apple", "regresyon"))
        for e, t, nt in rows
    )
    selin_clause_lin = any(
        ("selin" in nt or owner_of(e).lower().startswith("selin"))
        and "duzelt" in nt
        for e, t, nt in rows
    )

    compound_parent = any(
        ";" in (text_of(e, "before") or text_of(e, "after"))
        and e.get("stage") == "ACTION_COMPOUND_DECOMPOSITION"
        for e in events
    )
    title_context_loss = False
    if cue == 51 and can_a and not can_utf_ok:
        title_context_loss = True
    if cue == 51:
        for e, t, nt in rows:
            if e.get("stage") == "ACTION_COMPOUND_DECOMPOSITION" and "baslik" in nt and "utf" not in nt:
                title_context_loss = True

    answers = {
        "1_chunk": "NOT_OBSERVABLE",
        "2_gate": "NOT_OBSERVABLE",
        "3_raw_action_count": "NOT_OBSERVABLE",
        "4_raw_action_texts": "NOT_OBSERVABLE",
        "5_can_clause_raw_or_lineage": can_clause_lin or bool(can_a),
        "6_second_clause_raw_or_lineage": (burak_clause_lin or burak_ok) if cue == 51 else (selin_clause_lin or selin_ok),
        "7_compound_candidate": compound_parent if events else "NOT_OBSERVABLE",
        "8_decomposer_child_count": len(splits),
        "8b_not_split_count": len(not_splits),
        "9_can_owner_bound": bool(can_a and (can_a.get("owner") or "").startswith("Can")) if can_a else False,
        "10_other_owner_bound": (
            bool(burak_a and (burak_a.get("owner") or "").startswith("Burak"))
            if cue == 51 and burak_a
            else bool(selin_a and (selin_a.get("owner") or "").startswith("Selin"))
            if cue == 27 and selin_a
            else False
        ),
        "11_date_binding_events": sum(1 for e in events if e.get("stage") == "ACTION_RELATIVE_DATE_BINDING"),
        "12_title_backfill_events": len(backfills),
        "13_backfill_cue49": "NOT_OBSERVABLE",
        "14_dedup_drop": any(e.get("reasonCode") == "ACTION_DEDUPLICATED" for e in drops),
        "15_cross_type_drop": any("CROSS_TYPE" in str(e.get("reasonCode")) for e in drops),
        "16_validated_bundle": "NOT_OBSERVABLE",
        "17_final_note_mapped": {
            "can_or_first": can_ok,
            "can_utf8": can_utf_ok if cue == 51 else None,
            "second": burak_ok if cue == 51 else selin_ok,
        },
        "action_post_processing_splits": app.get("compoundActionsSplit"),
        "title_context_loss_observed": title_context_loss,
    }

    reason = []
    stage_fail = Counter()
    if cue == 51:
        if burak_ok and not can_utf_ok:
            if can_a and title_context_loss:
                reason.append("ACTION_TITLE_CONTEXT_LOSS")
                stage_fail["ACTION_TITLE_BACKFILL"] += 1
                if not backfills:
                    reason.append("ACTION_TITLE_BACKFILL_NOT_TRIGGERED")
                else:
                    reason.append("ACTION_TITLE_BACKFILL_INSUFFICIENT")
            elif splits and not can_ok:
                reason.append("COMPOUND_DECOMPOSER_PARTIAL_SPLIT")
                stage_fail["ACTION_COMPOUND_DECOMPOSITION"] += 1
            elif not can_clause_lin and events:
                reason.append("LLM_MISSED_FIRST_CLAUSE")
                stage_fail["LLM_RAW"] += 1
            elif events:
                reason.append("UNCLASSIFIED_MISS")
            else:
                reason.append("NOT_OBSERVABLE")
        elif can_utf_ok and burak_ok:
            reason.append("PASS")
        elif can_ok and burak_ok and not can_utf_ok:
            reason.append("ACTION_TITLE_CONTEXT_LOSS")
            stage_fail["ACTION_TITLE_BACKFILL"] += 1
        else:
            reason.append("UNCLASSIFIED_MISS" if events else "NOT_OBSERVABLE")
    else:
        if can_ok and not selin_ok:
            if splits:
                reason.append("COMPOUND_DECOMPOSER_PARTIAL_SPLIT")
                stage_fail["ACTION_COMPOUND_DECOMPOSITION"] += 1
            elif selin_clause_lin:
                reason.append("FINAL_NOTE_MAPPING_LOSS")
                stage_fail["FINAL_NOTE_MAPPING"] += 1
            else:
                reason.append("UNCLASSIFIED_MISS")
        elif can_ok and selin_ok:
            # weak selin title without time still counts as present via note_has duzelt
            reason.append("PASS")
        else:
            reason.append("UNCLASSIFIED_MISS" if events else "NOT_OBSERVABLE")

    return {
        "runDir": str(run_dir),
        "lineageEventCount": len(events),
        "stagesObserved": dict(stages),
        "operationsObserved": dict(ops),
        "splitEvents": len(splits),
        "notSplitEvents": len(not_splits),
        "dropEvents": len(drops),
        "finalNote": {
            "canPresent": can_ok,
            "canUtf8Present": can_utf_ok if cue == 51 else None,
            "secondPresent": burak_ok if cue == 51 else selin_ok,
            "canAction": can_a,
            "secondAction": burak_a if cue == 51 else selin_a,
        },
        "traceAnswers": answers,
        "reasonCodes": reason,
        "failureStages": dict(stage_fail),
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
            lines.append(f"- stages: {r['stagesObserved']}")
            lines.append(f"- final: {r['finalNote']}")
            lines.append(f"- answers: {json.dumps(r['traceAnswers'], ensure_ascii=False)}")
            lines.append("")
        (root / f"{name}.md").write_text("\n".join(lines) + "\n")
        print("wrote", root / f"{name}.json")

    c27 = json.loads((root / "cue-27-trace.json").read_text())
    c51 = json.loads((root / "cue-51-trace.json").read_text())

    def agg(trace):
        fails_b = fails_a = 0
        by_reason = Counter()
        by_stage = Counter()
        confirmed = []
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
            for s, n in (r.get("failureStages") or {}).items():
                by_stage[s] += n
        confirmed = [
            k
            for k, _ in by_reason.most_common()
            if k not in ("NOT_OBSERVABLE", "UNCLASSIFIED_MISS")
        ]
        return {
            "controlFailureCount": fails_b,
            "candidateFailureCount": fails_a,
            "failureCountsByStage": dict(by_stage),
            "failureCountsByReasonCode": dict(by_reason),
            "confirmedRootCauses": confirmed[:5],
        }

    a27, a51 = agg(c27), agg(c51)
    systemic = "NOT_OBSERVABLE"
    for key in (
        "ACTION_TITLE_CONTEXT_LOSS",
        "ACTION_TITLE_BACKFILL_NOT_TRIGGERED",
        "ACTION_TITLE_BACKFILL_INSUFFICIENT",
        "COMPOUND_DECOMPOSER_PARTIAL_SPLIT",
        "LLM_MISSED_FIRST_CLAUSE",
        "LLM_MERGED_COMPOUND_ACTION",
    ):
        if a51["failureCountsByReasonCode"].get(key):
            systemic = key
            break

    if systemic.startswith("ACTION_TITLE"):
        recommended = (
            "Enable/strengthen ActionTitleEvidenceBackfiller for stripped Can/title clauses "
            "(cue 49 UTF-8 context) without changing gate/cross-type/compound heuristics yet."
        )
        scope = ["action-title-backfill"]
    elif systemic in ("COMPOUND_DECOMPOSER_PARTIAL_SPLIT", "LLM_MERGED_COMPOUND_ACTION"):
        recommended = (
            "Fix compound split so Can+Burak clauses become two children with owner/date binding preserved."
        )
        scope = ["action-compound-decomposition"]
    elif systemic == "LLM_MISSED_FIRST_CLAUSE":
        recommended = "Prompt/extraction observability only so far — first clause absent before post-processing; do not change filters yet."
        scope = ["llm-raw-observability"]
    else:
        recommended = "Insufficient confirmed stage evidence; do not change extraction until campaign completes."
        scope = []

    # A vs B deltas from pass rates
    def pass_rate(trace, tree_prefix):
        runs = [r for r in trace["runs"] if r["tree"].startswith(tree_prefix) and not r["run"].startswith("smoke")]
        if not runs:
            return None
        ok = sum(1 for r in runs if r["reasonCodes"] == ["PASS"])
        return ok / len(runs)

    b51, a51p = pass_rate(c51, "B_"), pass_rate(c51, "A_")
    improvements, regressions = [], []
    if b51 is not None and a51p is not None:
        if a51p > b51:
            improvements.append(f"cue51_pass_rate A={a51p:.2f} > B={b51:.2f}")
        elif a51p < b51:
            regressions.append(f"cue51_pass_rate A={a51p:.2f} < B={b51:.2f}")

    summary = {
        "cue27": a27,
        "cue51": a51,
        "candidateImprovements": improvements,
        "candidateRegressions": regressions,
        "systemicRootCause": systemic,
        "recommendedFix": recommended,
        "recommendedFixScope": scope,
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
        + f"Cue51 reasons: `{a51['failureCountsByReasonCode']}`\n\n"
        + f"Cue51 stages: `{a51['failureCountsByStage']}`\n\n"
        + f"Recommended: {recommended}\n"
    )
    print("wrote compound-action-root-cause.json")


if __name__ == "__main__":
    main()
