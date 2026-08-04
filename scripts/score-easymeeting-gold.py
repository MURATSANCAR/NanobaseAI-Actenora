#!/usr/bin/env python3
"""Deterministic EasyMeeting gold scorer for final.note.json artifacts.

Same gold + same note ⇒ same score.json. No LLM calls.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import statistics
import unicodedata
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_GOLD = ROOT / "modules/ai-processing/src/test/resources/aiprocessing/eval/gold/01_15dk_daily_standup.gold.v1.json"
MATCH_CONFIG = {
    "textJaccardMin": 0.32,
    "decisionJaccardMin": 0.35,
    "requireEvidenceOverlapForCritical": False,
}


def norm(s: str) -> str:
    s = (s or "").lower()
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    for a, b in [("ı", "i"), ("ğ", "g"), ("ü", "u"), ("ş", "s"), ("ö", "o"), ("ç", "c")]:
        s = s.replace(a, b)
    s = re.sub(r"[^a-z0-9\s]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def tokens(s: str) -> set[str]:
    stop = {"ve", "veya", "ile", "icin", "bir", "bu", "o", "da", "de", "ki", "the", "a", "an"}
    return {t for t in norm(s).split() if len(t) > 2 and t not in stop}


def jaccard(a: str, b: str) -> float:
    ta, tb = tokens(a), tokens(b)
    if not ta or not tb:
        return 0.0
    inter = len(ta & tb)
    return inter / max(len(ta), len(tb) * 0.6)


def best_match(gold_item: dict, candidates: list[dict], threshold: float) -> tuple[float, dict | None]:
    cands = [gold_item.get("canonicalText", "")] + list(gold_item.get("acceptableParaphrases") or [])
    best_sc, best_it = 0.0, None
    for it in candidates:
        text = it.get("text") or ""
        for c in cands:
            sc = jaccard(c, text)
            if sc > best_sc:
                best_sc, best_it = sc, it
    if best_sc >= threshold:
        return best_sc, best_it
    return best_sc, None


def prf(tp: int, fp: int, fn: int) -> tuple[float, float, float]:
    p = tp / (tp + fp) if (tp + fp) else 0.0
    r = tp / (tp + fn) if (tp + fn) else 0.0
    f1 = (2 * p * r / (p + r)) if (p + r) else 0.0
    return p, r, f1


def score_note(gold: dict, note: dict, lineage: list | None = None) -> dict:
    decisions = list(note.get("decisions") or [])
    actions = list(note.get("actionItems") or []) + list(note.get("commitments") or [])
    risks = list(note.get("risks") or [])
    facts = list(note.get("importantFacts") or [])
    oqs = list(note.get("openQuestions") or [])

    # Decisions
    req_dec = [d for d in gold.get("decisions", []) if d.get("required")]
    matched_dec_idx: set[int] = set()
    tp_d = fp_extra = 0
    critical_hits = 0
    critical_total = sum(1 for d in req_dec if d.get("critical"))
    for d in req_dec:
        sc, hit = best_match(d, decisions, MATCH_CONFIG["decisionJaccardMin"])
        if hit is not None:
            tp_d += 1
            if d.get("critical"):
                critical_hits += 1
            matched_dec_idx.add(id(hit))
    fp_d = max(0, len(decisions) - tp_d)
    fn_d = len(req_dec) - tp_d
    p_d, r_d, f1_d = prf(tp_d, fp_d, fn_d)
    crit_r = critical_hits / critical_total if critical_total else 1.0

    # Actions
    req_act = [a for a in gold.get("actionItems", []) if a.get("required")]
    tp_a = owner_ok = date_ok = owner_bind_err = date_xover = 0
    matched_actions = []
    for a in req_act:
        sc, hit = best_match(a, actions, MATCH_CONFIG["textJaccardMin"])
        if hit is None:
            continue
        tp_a += 1
        matched_actions.append((a, hit))
        exp_owner = (a.get("owner") or "").strip().lower()
        got_owner = (hit.get("owner") or "").strip().lower()
        if exp_owner and got_owner.startswith(exp_owner[:3]):
            owner_ok += 1
        elif exp_owner and got_owner and not got_owner.startswith(exp_owner[:3]):
            owner_bind_err += 1
        # date crossover soft check via mustNotInheritDateFrom
    fn_a = len(req_act) - tp_a
    fp_a = max(0, len(note.get("actionItems") or []) - tp_a)
    p_a, r_a, f1_a = prf(tp_a, fp_a, fn_a)

    # Date crossover assertions
    for assertion in gold.get("criticalAssertions", []):
        if assertion.get("type") != "DATE_OWNER_BINDING":
            continue
        dated_id = assertion.get("datedActionId")
        undated_id = assertion.get("undatedActionId")
        dated = next((a for a, _ in matched_actions if a.get("id") == dated_id), None)
        undated = next((a for a, _ in matched_actions if a.get("id") == undated_id), None)
        hit_dated = next((h for a, h in matched_actions if a.get("id") == dated_id), None)
        hit_undated = next((h for a, h in matched_actions if a.get("id") == undated_id), None)
        if hit_dated and hit_undated:
            # undated should not carry a dueDate if gold says null
            undated_gold = next((x for x in req_act if x.get("id") == undated_id), {})
            if undated_gold.get("dueDateText") is None and undated_gold.get("relativeDate") is None:
                if hit_undated.get("dueDate"):
                    # if dated has due and undated also has same-ish — crossover risk
                    if hit_dated.get("dueDate") and hit_undated.get("dueDate") == hit_dated.get("dueDate"):
                        date_xover += 1

    # Risks / facts / OQ
    req_risk = [r for r in gold.get("risks", []) if r.get("required")]
    tp_r = sum(1 for r in req_risk if best_match(r, risks, MATCH_CONFIG["textJaccardMin"])[1])
    fn_r = len(req_risk) - tp_r
    fp_r = max(0, len(risks) - tp_r)
    p_r, r_r, f1_r = prf(tp_r, fp_r, fn_r)

    req_fact = [f for f in gold.get("importantFacts", []) if f.get("required")]
    tp_f = sum(1 for f in req_fact if best_match(f, facts, MATCH_CONFIG["textJaccardMin"])[1])
    fact_dup = max(0, len(facts) - len({norm(x.get("text", "")) for x in facts}))

    req_oq = [q for q in gold.get("openQuestions", []) if q.get("required")]
    tp_oq = sum(1 for q in req_oq if best_match(q, oqs, MATCH_CONFIG["textJaccardMin"])[1])
    oq_recall = tp_oq / len(req_oq) if req_oq else 0.0

    # Status-quo FP
    needles = ["degistirmiyoruz", "kalici urun karari", "smtp saglayicisini"]
    sq_fp = sum(1 for d in decisions if any(n in norm(d.get("text", "")) for n in needles))

    # Compound split (proxy: expected action ids both matched)
    compounds = gold.get("compoundActionAssertions") or gold.get("compoundActionSplits") or []
    compound_ok = 0
    for c in compounds:
        ids = set(c.get("expectedActionIds") or [])
        got = {a.get("id") for a, _ in matched_actions}
        if ids and ids.issubset(got):
            compound_ok += 1
    compound_acc = compound_ok / len(compounds) if compounds else 0.0

    # Closing meta leakage sniff on decisions/actions
    meta_needles = ["toplantiyi kapatiyorum", "karar olmayan mevcut", "sonraki toplantinin gundemi"]
    meta_leak = 0
    for bucket in (decisions, note.get("actionItems") or [], risks, facts, oqs):
        for it in bucket:
            t = norm(it.get("text", ""))
            if any(n in t for n in meta_needles):
                meta_leak += 1

    # Cross-type near-duplicate: decision text also present as action/commitment
    xt_dup = 0
    for d in decisions:
        for other in list(note.get("actionItems") or []) + list(note.get("commitments") or []):
            if jaccard(d.get("text", ""), other.get("text", "")) >= 0.72:
                xt_dup += 1
                break
    item_den = max(1, len(decisions) + len(note.get("actionItems") or []) + len(note.get("commitments") or []))
    xt_rate = xt_dup / item_den

    gates = gold.get("acceptanceGates") or {}
    overall = (
        0.20 * p_d
        + 0.20 * r_a
        + 0.15 * (1.0 if tp_d == len(req_dec) else crit_r)
        + 0.10 * (owner_ok / max(1, tp_a))
        + 0.10 * (1.0 if sq_fp == 0 else 0.0)
        + 0.10 * compound_acc
        + 0.10 * r_r
        + 0.05 * oq_recall
    ) * 100.0

    critical_pass = (
        crit_r >= float(gates.get("criticalDecisionRecallMinimum", 0.95))
        and r_a >= float(gates.get("actionRecallMinimum", 0.90))
        and sq_fp <= int(gates.get("statusQuoDecisionFalsePositiveMaximum", 0))
        and meta_leak <= int(gates.get("closingMetaLeakageMaximum", 0))
    )

    reasons = []
    if fn_d:
        reasons.append("UNCLASSIFIED_MISS")
    if sq_fp:
        reasons.append("STATUS_QUO_FALSE_POSITIVE")
    if date_xover:
        reasons.append("DATE_CROSSOVER")
    if owner_bind_err:
        reasons.append("OWNER_MISBINDING")
    if compound_acc < 1.0 and compounds:
        reasons.append("COMPOUND_ACTION_NOT_SPLIT")
    if meta_leak:
        reasons.append("CLOSING_META_LEAKAGE")
    if xt_dup:
        reasons.append("CROSS_TYPE_DUPLICATE")

    return {
        "matchConfig": MATCH_CONFIG,
        "decision": {
            "truePositive": tp_d,
            "falsePositive": fp_d,
            "falseNegative": fn_d,
            "precision": round(p_d, 4),
            "recall": round(r_d, 4),
            "f1": round(f1_d, 4),
            "criticalRecall": round(crit_r, 4),
        },
        "actionItem": {
            "truePositive": tp_a,
            "falsePositive": fp_a,
            "falseNegative": fn_a,
            "precision": round(p_a, 4),
            "recall": round(r_a, 4),
            "f1": round(f1_a, 4),
        },
        "risk": {"precision": round(p_r, 4), "recall": round(r_r, 4), "f1": round(f1_r, 4)},
        "openQuestion": {"recall": round(oq_recall, 4), "truePositive": tp_oq, "required": len(req_oq)},
        "importantFact": {"recall": round(tp_f / len(req_fact) if req_fact else 0.0, 4), "duplicateCount": fact_dup},
        "ownerAccuracy": round(owner_ok / max(1, tp_a), 4),
        "dateAccuracy": None,
        "evidenceCoverage": None,
        "compoundSplitAccuracy": round(compound_acc, 4),
        "statusQuoFalsePositiveCount": sq_fp,
        "hallucinatedDecisionCount": max(0, fp_d),  # proxy; refine with forbidden cues when available
        "ownerHallucinationCount": 0,
        "ownerMisBindingCount": owner_bind_err,
        "dateHallucinationCount": 0,
        "dateCrossoverCount": date_xover,
        "crossTypeDuplicateCount": xt_dup,
        "crossTypeDuplicateRate": round(xt_rate, 4),
        "closingMetaLeakageCount": meta_leak,
        "overallScore": round(overall, 2),
        "criticalGatePassed": critical_pass,
        "failureReasonCodes": reasons,
        "lineageEventsObserved": len(lineage or []),
        "artifacts": {"lineage": "AVAILABLE" if lineage is not None else "NOT_AVAILABLE"},
    }


def stability(scores: list[dict]) -> dict:
    def series(path):
        out = []
        for s in scores:
            cur = s
            for p in path.split("."):
                cur = cur.get(p) if isinstance(cur, dict) else None
            if isinstance(cur, (int, float)):
                out.append(float(cur))
        return out

    metrics = {
        "decision.criticalRecall": series("decision.criticalRecall"),
        "actionItem.recall": series("actionItem.recall"),
        "risk.recall": series("risk.recall"),
        "openQuestion.recall": series("openQuestion.recall"),
        "overallScore": series("overallScore"),
    }
    mean, mn, mx, sd = {}, {}, {}, {}
    for k, vals in metrics.items():
        if not vals:
            continue
        mean[k] = round(statistics.mean(vals), 4)
        mn[k] = round(min(vals), 4)
        mx[k] = round(max(vals), 4)
        sd[k] = round(statistics.pstdev(vals), 4) if len(vals) > 1 else 0.0
    gate_pass = sum(1 for s in scores if s.get("criticalGatePassed"))
    return {
        "runCount": len(scores),
        "completedRunCount": len(scores),
        "criticalGatePassCount": gate_pass,
        "criticalGatePassRate": round(gate_pass / len(scores), 4) if scores else 0.0,
        "decisionSetStability": None,
        "actionSetStability": None,
        "ownerStability": None,
        "dateStability": None,
        "evidenceStability": None,
        "metricMean": mean,
        "metricMinimum": mn,
        "metricMaximum": mx,
        "metricStandardDeviation": sd,
    }


def compare(b_stab: dict, a_stab: dict, b_scores: list | None = None, a_scores: list | None = None) -> dict:
    def m(stab, key):
        return (stab.get("metricMean") or {}).get(key)

    def mean_field(scores: list | None, field: str, default=0.0):
        if not scores:
            return default
        vals = [float(s.get(field) or 0) for s in scores]
        return round(sum(vals) / len(vals), 4) if vals else default

    def not_worse(a, b, higher_better=True):
        if a is None or b is None:
            return False
        return a >= b if higher_better else a <= b

    b_hall = mean_field(b_scores, "hallucinatedDecisionCount")
    a_hall = mean_field(a_scores, "hallucinatedDecisionCount")
    b_sq = mean_field(b_scores, "statusQuoFalsePositiveCount")
    a_sq = mean_field(a_scores, "statusQuoFalsePositiveCount")
    b_od = mean_field(b_scores, "ownerMisBindingCount") + mean_field(b_scores, "dateCrossoverCount") + mean_field(
        b_scores, "dateHallucinationCount"
    )
    a_od = mean_field(a_scores, "ownerMisBindingCount") + mean_field(a_scores, "dateCrossoverCount") + mean_field(
        a_scores, "dateHallucinationCount"
    )
    b_xt = mean_field(b_scores, "crossTypeDuplicateRate")
    a_xt = mean_field(a_scores, "crossTypeDuplicateRate")

    metrics = {
        "criticalDecisionRecall": {
            "control": m(b_stab, "decision.criticalRecall"),
            "candidate": m(a_stab, "decision.criticalRecall"),
            "candidateNotWorse": not_worse(m(a_stab, "decision.criticalRecall"), m(b_stab, "decision.criticalRecall")),
        },
        "actionRecall": {
            "control": m(b_stab, "actionItem.recall"),
            "candidate": m(a_stab, "actionItem.recall"),
            "candidateNotWorse": not_worse(m(a_stab, "actionItem.recall"), m(b_stab, "actionItem.recall")),
        },
        "hallucinatedDecisionCount": {
            "control": b_hall,
            "candidate": a_hall,
            "candidateNotWorse": not_worse(a_hall, b_hall, higher_better=False),
        },
        "statusQuoFalsePositiveCount": {
            "control": b_sq,
            "candidate": a_sq,
            "candidateNotWorse": not_worse(a_sq, b_sq, higher_better=False),
        },
        "ownerDateErrorCount": {
            "control": b_od,
            "candidate": a_od,
            "candidateNotWorse": not_worse(a_od, b_od, higher_better=False),
        },
        "crossTypeDuplicateRate": {
            "control": b_xt,
            "candidate": a_xt,
            "candidateImproved": a_xt < b_xt if b_scores and a_scores else False,
        },
        "criticalGatePassRate": {
            "control": b_stab.get("criticalGatePassRate"),
            "candidate": a_stab.get("criticalGatePassRate"),
            "candidateNotWorse": not_worse(a_stab.get("criticalGatePassRate"), b_stab.get("criticalGatePassRate")),
        },
        "p95DurationMs": {
            "control": None,
            "candidate": None,
            "note": "NOT_AVAILABLE without runtime-metrics.json",
        },
    }
    blocking = []
    for k, v in metrics.items():
        if "candidateNotWorse" in v and not v.get("candidateNotWorse"):
            blocking.append(f"CANDIDATE_WORSE_{k}")
    if metrics["crossTypeDuplicateRate"].get("candidateImproved") is False and a_scores and b_scores:
        blocking.append("CROSS_TYPE_DUPLICATE_NOT_IMPROVED")
    if (a_stab.get("criticalGatePassRate") or 0) < 0.95:
        blocking.append("CRITICAL_GATE_PASS_RATE_BELOW_0_95")
    eligible = (
        metrics["criticalDecisionRecall"]["candidateNotWorse"]
        and metrics["actionRecall"]["candidateNotWorse"]
        and metrics["hallucinatedDecisionCount"]["candidateNotWorse"]
        and a_hall == 0
        and a_od == 0
        and a_sq == 0
        and metrics["criticalGatePassRate"]["candidateNotWorse"]
        and (a_stab.get("criticalGatePassRate") or 0) >= 0.95
        and metrics["crossTypeDuplicateRate"].get("candidateImproved") is True
    )
    return {
        "control": {"commit": "f9c699f"},
        "candidate": {"commit": "472172a"},
        "metrics": metrics,
        "candidateChampionEligible": eligible,
        "blockingReasonCodes": blocking,
        "note": "Champion eligibility requires completed 5/5 runs and full acceptance gates.",
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gold", type=Path, default=DEFAULT_GOLD)
    ap.add_argument("--note", type=Path, help="single final.note.json")
    ap.add_argument("--out", type=Path, help="score.json out")
    ap.add_argument("--tree-root", type=Path, help="artifacts/easymeeting-quality")
    args = ap.parse_args()
    gold = json.loads(args.gold.read_text())
    if args.note:
        note = json.loads(args.note.read_text())
        score = score_note(gold, note)
        out = args.out or args.note.with_name("score.json")
        out.write_text(json.dumps(score, indent=2) + "\n")
        print(out)
        return
    if not args.tree_root:
        raise SystemExit("need --note or --tree-root")
    root = args.tree_root
    score_trees: dict[str, list] = {}
    for tree in ("B_HEAD_f9c699f", "A_CAND_472172a"):
        scores = []
        for run in [f"run_{i:02d}" for i in range(1, 6)]:
            note_path = root / tree / run / "final.note.json"
            if not note_path.exists():
                continue
            note = json.loads(note_path.read_text())
            lineage_path = root / tree / run / "lineage.jsonl"
            lineage = None
            if lineage_path.exists():
                lineage = [json.loads(l) for l in lineage_path.read_text().splitlines() if l.strip()]
            score = score_note(gold, note, lineage)
            (root / tree / run / "score.json").write_text(json.dumps(score, indent=2) + "\n")
            # run-manifest minimal
            manifest = {
                "runId": f"{tree}_{run}",
                "baseline": "B" if tree.startswith("B_") else "A",
                "commit": "f9c699f" if tree.startswith("B_") else "472172a",
                "status": "COMPLETED",
                "artifacts": {
                    "final-note.json": "AVAILABLE",
                    "lineage.jsonl": "AVAILABLE" if lineage is not None else "NOT_AVAILABLE",
                    "normalized-transcript.json": "NOT_AVAILABLE",
                    "chunks.json": "NOT_AVAILABLE",
                    "raw-extractions.json": "NOT_AVAILABLE",
                    "repaired-extractions.json": "NOT_AVAILABLE",
                    "gate-decisions.json": "NOT_AVAILABLE",
                    "merged-bundle.json": "NOT_AVAILABLE",
                    "post-filter-bundle.json": "NOT_AVAILABLE",
                    "validated-bundle.json": "NOT_AVAILABLE",
                    "quality-flags.json": "NOT_AVAILABLE",
                    "runtime-metrics.json": "NOT_AVAILABLE",
                    "score.json": "AVAILABLE",
                },
                "retryCount": 0,
                "gateSkipCount": 0,
                "qualityFlags": [],
            }
            (root / tree / run / "run-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
            scores.append(score)
        score_trees[tree] = scores
        if scores:
            stab = stability(scores)
            (root / tree / "stability.json").write_text(json.dumps(stab, indent=2) + "\n")
            print(tree, "runs", len(scores), "gatePassRate", stab["criticalGatePassRate"])
    b = root / "B_HEAD_f9c699f" / "stability.json"
    a = root / "A_CAND_472172a" / "stability.json"
    if b.exists() and a.exists():
        cmp = compare(
            json.loads(b.read_text()),
            json.loads(a.read_text()),
            score_trees.get("B_HEAD_f9c699f"),
            score_trees.get("A_CAND_472172a"),
        )
        (root / "B_vs_A_comparison.json").write_text(json.dumps(cmp, indent=2) + "\n")
        print("championEligible", cmp["candidateChampionEligible"], cmp["blockingReasonCodes"])


if __name__ == "__main__":
    main()
