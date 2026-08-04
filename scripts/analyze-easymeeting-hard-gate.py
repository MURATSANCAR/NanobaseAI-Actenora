#!/usr/bin/env python3
"""Phase 1B hard-gate failure matrix + root-cause summary.

Deterministic. Uses gold + final.note.json only when intermediates are absent.
Does not mutate run artifacts. Never invents lineage events.
"""
from __future__ import annotations

import argparse
import json
import re
import unicodedata
from collections import Counter, defaultdict
from copy import deepcopy
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_GOLD = ROOT / "modules/ai-processing/src/test/resources/aiprocessing/eval/gold/01_15dk_daily_standup.gold.v1.json"
DEFAULT_TREE = ROOT / "artifacts/easymeeting-quality"

MATCH = {
    "textJaccardMin": 0.32,
    "decisionJaccardMin": 0.35,
}

STATUSES = {
    "PASS",
    "MISS",
    "WRONG_TYPE",
    "WRONG_OWNER",
    "WRONG_DATE",
    "DATE_CROSSOVER",
    "DUPLICATED",
    "FALSELY_DROPPED",
    "NOT_MAPPED",
    "UNCLASSIFIED",
}

NOT_OBSERVABLE = "NOT_OBSERVABLE"
STAGE_CHAIN = [
    "chunkAssignment",
    "chunkGate",
    "rawLlmExtraction",
    "jsonRepair",
    "schemaValidation",
    "grounding",
    "merge",
    "speechAct",
    "meetingItemPolicy",
    "actionPostProcessing",
    "crossType",
    "validatedBundle",
    "finalNote",
]


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
    return len(ta & tb) / max(len(ta), len(tb) * 0.6)


def best_match(gold_item: dict, candidates: list[dict], threshold: float) -> tuple[float, dict | None, int | None]:
    phrases = [gold_item.get("canonicalText", "")] + list(gold_item.get("acceptableParaphrases") or [])
    best_sc, best_it, best_i = 0.0, None, None
    for i, it in enumerate(candidates):
        text = it.get("text") or ""
        for c in phrases:
            sc = jaccard(c, text)
            if sc > best_sc:
                best_sc, best_it, best_i = sc, it, i
    if best_sc >= threshold:
        return best_sc, best_it, best_i
    return best_sc, None, None


def owner_ok(expected: str | None, got: str | None) -> bool:
    if not expected:
        return True
    e = (expected or "").strip().lower()
    g = (got or "").strip().lower()
    if not g:
        return False
    return g.startswith(e[:3]) if len(e) >= 3 else e == g


def has_date(item: dict) -> bool:
    return bool(item.get("dueDate") or item.get("dueAt") or item.get("relativeDate"))


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def artifact_availability(run_dir: Path) -> dict[str, str]:
    names = {
        "final.note.json": "finalNote",
        "lineage.jsonl": "lineage",
        "raw-extractions.json": "rawExtractions",
        "repaired-extractions.json": "repairedExtractions",
        "gate-decisions.json": "gateDecisions",
        "merged-bundle.json": "mergedBundle",
        "validated-bundle.json": "validatedBundle",
        "chunks.json": "chunks",
    }
    out = {}
    for file, key in names.items():
        out[key] = "AVAILABLE" if (run_dir / file).exists() else "NOT_AVAILABLE"
    # alternate naming
    if out["finalNote"] == "NOT_AVAILABLE" and (run_dir / "final-note.json").exists():
        out["finalNote"] = "AVAILABLE"
    return out


def empty_chain(final_present: bool | None = None) -> dict[str, str]:
    chain = {k: NOT_OBSERVABLE for k in STAGE_CHAIN}
    if final_present is True:
        chain["finalNote"] = "PRESENT"
    elif final_present is False:
        chain["finalNote"] = "ABSENT"
    return chain


def classify_item(
    gold: dict,
    note_bucket: list[dict],
    *,
    threshold: float,
    check_owner: bool = False,
    check_date_null: bool = False,
) -> dict[str, Any]:
    sc, hit, _ = best_match(gold, note_bucket, threshold)
    gid = gold["id"]
    result = {
        "goldId": gid,
        "status": "MISS",
        "score": round(sc, 4),
        "matchedText": (hit or {}).get("text") if hit else None,
        "matchedOwner": (hit or {}).get("owner") if hit else None,
        "matchedDueDate": (hit or {}).get("dueDate") if hit else None,
        "reasonCodes": [],
        "lifecycle": empty_chain(final_present=bool(hit)),
    }
    if hit is None:
        # Without intermediate artifacts we cannot prove which stage dropped it.
        result["reasonCodes"] = ["UNCLASSIFIED_MISS", "NOT_OBSERVABLE"]
        result["lifecycle"] = empty_chain(False)
        return result

    status = "PASS"
    reasons: list[str] = []
    if check_owner:
        exp = gold.get("owner")
        if exp and not owner_ok(exp, hit.get("owner")):
            status = "WRONG_OWNER"
            reasons.append("OWNER_MISBINDING")
    # relativeDate expected null → must not inherit date
    if check_date_null:
        gold_null = gold.get("dueDateText") is None and gold.get("relativeDate") is None
        if gold_null and has_date(hit):
            # may be crossover; marked later at assertion level
            if status == "PASS":
                status = "WRONG_DATE"
            reasons.append("DATE_HALLUCINATION")
    # expected relative date present?
    if gold.get("relativeDate") and not has_date(hit):
        if status == "PASS":
            status = "WRONG_DATE"
        reasons.append("UNCLASSIFIED_MISS")

    result["status"] = status
    result["reasonCodes"] = reasons
    result["lifecycle"]["finalNote"] = "PRESENT"
    return result


def status_quo_errors(note: dict) -> list[dict]:
    needles = {
        19: ["degistirmiyoruz", "kalici urun karari", "token suresini"],
        43: ["smtp saglayicisini", "smtp"],
    }
    out = []
    for d in note.get("decisions") or []:
        t = norm(d.get("text", ""))
        for cue, ns in needles.items():
            if any(n in t for n in ns):
                out.append(
                    {
                        "cueId": cue,
                        "text": d.get("text"),
                        "reasonCode": "STATUS_QUO_FALSE_POSITIVE",
                        "observedIn": "final.note.decisions",
                    }
                )
    return out


def meta_leakage(note: dict) -> list[dict]:
    needles = ["toplantiyi kapatiyorum", "karar olmayan mevcut", "sonraki toplantinin gundemi", "gundem ve beklenen"]
    out = []
    for bucket_name in ("decisions", "actionItems", "risks", "importantFacts", "openQuestions", "proposals", "commitments"):
        for it in note.get(bucket_name) or []:
            t = norm(it.get("text", ""))
            if any(n in t for n in needles):
                out.append({"bucket": bucket_name, "text": it.get("text"), "reasonCode": "CLOSING_META_LEAKAGE"})
    return out


def cross_type_duplicates(note: dict) -> list[dict]:
    out = []
    decisions = note.get("decisions") or []
    others = list(note.get("actionItems") or []) + list(note.get("commitments") or [])
    for d in decisions:
        for o in others:
            if jaccard(d.get("text", ""), o.get("text", "")) >= 0.72:
                out.append(
                    {
                        "decision": d.get("text"),
                        "other": o.get("text"),
                        "otherOwner": o.get("owner"),
                        "reasonCode": "CROSS_TYPE_DUPLICATE",
                    }
                )
                break
    return out


def compound_checks(gold: dict, item_results: dict[str, dict], note: dict) -> list[dict]:
    errors = []
    actions = list(note.get("actionItems") or [])
    assertions = gold.get("compoundActionAssertions") or gold.get("compoundActionSplits") or []
    for a in assertions:
        ids = a.get("expectedActionIds") or []
        cue = a.get("cueId")
        statuses = {i: item_results.get(i, {}).get("status") for i in ids}
        if any(statuses.get(i) == "MISS" for i in ids):
            errors.append(
                {
                    "id": a.get("id") or f"COMPOUND-{cue}",
                    "cueId": cue,
                    "expectedActionIds": ids,
                    "statuses": statuses,
                    "reasonCode": "ACTION_COMPOUND_NOT_SPLIT",
                    "detail": "one or more expected split actions missing from final note",
                }
            )
        # date crossover via gold criticalAssertions
    for assertion in gold.get("criticalAssertions") or []:
        if assertion.get("type") != "DATE_OWNER_BINDING":
            continue
        dated_id = assertion.get("datedActionId")
        undated_id = assertion.get("undatedActionId")
        dated = item_results.get(dated_id, {})
        undated = item_results.get(undated_id, {})
        if dated.get("status") in (None, "MISS") or undated.get("status") in (None, "MISS"):
            continue
        # Find matched hits from note via results
        undated_hit_date = undated.get("matchedDueDate")
        dated_hit_date = dated.get("matchedDueDate")
        # also scan note for undated gold owner text having dueDate equal to dated
        if undated_hit_date and dated_hit_date and undated_hit_date == dated_hit_date:
            errors.append(
                {
                    "id": assertion.get("id"),
                    "datedActionId": dated_id,
                    "undatedActionId": undated_id,
                    "reasonCode": "DATE_CROSSOVER",
                    "detail": f"both carry dueDate={undated_hit_date}",
                }
            )
            if undated.get("status") == "PASS":
                undated["status"] = "DATE_CROSSOVER"
            undated.setdefault("reasonCodes", []).append("DATE_CROSSOVER")
    return errors


def analyze_run(run_id: str, baseline: str, commit: str, gold: dict, note: dict, avail: dict[str, str]) -> dict:
    decisions = list(note.get("decisions") or [])
    actions = list(note.get("actionItems") or []) + list(note.get("commitments") or [])
    risks = list(note.get("risks") or [])
    facts = list(note.get("importantFacts") or [])
    oqs = list(note.get("openQuestions") or [])

    item_results: dict[str, dict] = {}

    for d in gold.get("decisions") or []:
        if not d.get("required"):
            continue
        item_results[d["id"]] = classify_item(d, decisions, threshold=MATCH["decisionJaccardMin"])

    for a in gold.get("actionItems") or []:
        if not a.get("required"):
            continue
        r = classify_item(
            a,
            actions,
            threshold=MATCH["textJaccardMin"],
            check_owner=True,
            check_date_null=(a.get("relativeDate") is None and a.get("dueDateText") is None),
        )
        item_results[a["id"]] = r

    for r in gold.get("risks") or []:
        if r.get("required"):
            item_results[r["id"]] = classify_item(r, risks, threshold=MATCH["textJaccardMin"])

    for f in gold.get("importantFacts") or []:
        if f.get("required"):
            item_results[f["id"]] = classify_item(f, facts, threshold=MATCH["textJaccardMin"])

    for q in gold.get("openQuestions") or []:
        if q.get("required"):
            item_results[q["id"]] = classify_item(q, oqs, threshold=MATCH["textJaccardMin"])

    sq = status_quo_errors(note)
    meta = meta_leakage(note)
    xt = cross_type_duplicates(note)
    compound_errs = compound_checks(gold, item_results, note)

    owner_errors = [
        {"goldId": gid, "expectedOwner": next((a.get("owner") for a in gold.get("actionItems", []) if a.get("id") == gid), None),
         "gotOwner": res.get("matchedOwner"), "reasonCode": "OWNER_MISBINDING"}
        for gid, res in item_results.items()
        if res.get("status") == "WRONG_OWNER"
    ]
    date_errors = [
        {"goldId": gid, "status": res.get("status"), "matchedDueDate": res.get("matchedDueDate"),
         "reasonCodes": res.get("reasonCodes")}
        for gid, res in item_results.items()
        if res.get("status") in ("WRONG_DATE", "DATE_CROSSOVER") or "DATE_CROSSOVER" in (res.get("reasonCodes") or [])
    ]

    missing = [{"goldId": gid, "status": res["status"], "reasonCodes": res["reasonCodes"]}
               for gid, res in item_results.items() if res["status"] == "MISS"]
    fps = [{"text": d.get("text"), "reasonCode": "LLM_HALLUCINATION"} for d in decisions
           if best_match({"canonicalText": d.get("text", ""), "acceptableParaphrases": []},
                         [x for x in gold.get("decisions", []) if x.get("required")],
                         MATCH["decisionJaccardMin"])[1] is None]

    # Critical gate (same as scorer gates)
    gates = gold.get("acceptanceGates") or {}
    crit_ids = [d["id"] for d in gold.get("decisions", []) if d.get("required") and d.get("critical")]
    crit_hits = sum(1 for i in crit_ids if item_results.get(i, {}).get("status") == "PASS")
    crit_r = crit_hits / len(crit_ids) if crit_ids else 1.0
    act_ids = [a["id"] for a in gold.get("actionItems", []) if a.get("required")]
    act_hits = sum(1 for i in act_ids if item_results.get(i, {}).get("status") not in ("MISS",))
    # recall uses PASS or owner/date wrong still "recalled"
    act_recall_hits = sum(1 for i in act_ids if item_results.get(i, {}).get("status") != "MISS")
    act_r = act_recall_hits / len(act_ids) if act_ids else 1.0
    critical_gate = (
        crit_r >= float(gates.get("criticalDecisionRecallMinimum", 0.95))
        and act_r >= float(gates.get("actionRecallMinimum", 0.90))
        and len(sq) <= int(gates.get("statusQuoDecisionFalsePositiveMaximum", 0))
        and len(meta) <= int(gates.get("closingMetaLeakageMaximum", 0))
    )

    failed_assertions = []
    if crit_r < float(gates.get("criticalDecisionRecallMinimum", 0.95)):
        failed_assertions.append("criticalDecisionRecall")
    if act_r < float(gates.get("actionRecallMinimum", 0.90)):
        failed_assertions.append("actionRecall")
    if sq:
        failed_assertions.append("statusQuoFalsePositive")
    if meta:
        failed_assertions.append("closingMetaLeakage")
    if any(e.get("reasonCode") == "ACTION_COMPOUND_NOT_SPLIT" for e in compound_errs):
        failed_assertions.append("compoundActionSplit")
    if any(e.get("reasonCode") == "DATE_CROSSOVER" for e in compound_errs):
        failed_assertions.append("dateCrossover")

    root_codes: list[str] = []
    for res in item_results.values():
        root_codes.extend(res.get("reasonCodes") or [])
    for e in sq:
        root_codes.append(e["reasonCode"])
    for e in meta:
        root_codes.append(e["reasonCode"])
    for e in xt:
        root_codes.append(e["reasonCode"])
    for e in compound_errs:
        root_codes.append(e["reasonCode"])
    if missing and all(c == "UNCLASSIFIED_MISS" or c == "NOT_OBSERVABLE" for m in missing for c in m["reasonCodes"]):
        pass
    # Deduplicate preserving order
    seen = set()
    root_unique = []
    for c in root_codes:
        if c not in seen:
            seen.add(c)
            root_unique.append(c)

    # Matrix cell statuses for required columns
    matrix_cells = {}
    for gid in ["D-01", "D-02", "A-01", "A-02", "A-03", "A-04", "A-05", "A-06", "A-07"]:
        matrix_cells[gid] = item_results.get(gid, {}).get("status", "MISS")

    # compound cells
    def compound_cell(cue: int, ids: list[str]) -> str:
        st = [item_results.get(i, {}).get("status") for i in ids]
        if all(s == "PASS" for s in st):
            # check crossover in compound_errs
            if any(e.get("cueId") == cue or e.get("datedActionId") in ids for e in compound_errs if e.get("reasonCode") == "DATE_CROSSOVER"):
                return "DATE_CROSSOVER"
            return "PASS"
        if any(s == "MISS" for s in st):
            return "MISS"
        if any(s == "DATE_CROSSOVER" for s in st):
            return "DATE_CROSSOVER"
        if any(s == "WRONG_OWNER" for s in st):
            return "WRONG_OWNER"
        return "UNCLASSIFIED"

    matrix_cells["CUE27_SPLIT"] = compound_cell(27, ["A-03", "A-04"])
    matrix_cells["CUE51_SPLIT"] = compound_cell(51, ["A-06", "A-07"])
    matrix_cells["STATUS_QUO"] = "PASS" if not sq else "FALSE_POSITIVE"
    matrix_cells["OWNER_DATE"] = "PASS" if not owner_errors and not date_errors else (
        "DATE_CROSSOVER" if date_errors else "WRONG_OWNER"
    )
    matrix_cells["DUPLICATE"] = "PASS" if not xt else "DUPLICATED"
    matrix_cells["META_LEAKAGE"] = "PASS" if not meta else "FALSE_POSITIVE"

    return {
        "runId": run_id,
        "baseline": baseline,
        "commit": commit,
        "criticalGatePassed": critical_gate,
        "metrics": {
            "criticalDecisionRecall": round(crit_r, 4),
            "actionRecall": round(act_r, 4),
            "statusQuoFalsePositiveCount": len(sq),
            "closingMetaLeakageCount": len(meta),
            "crossTypeDuplicateCount": len(xt),
        },
        "failedAssertions": failed_assertions,
        "missingGoldItems": missing,
        "falsePositiveItems": fps,
        "ownerErrors": owner_errors,
        "dateErrors": date_errors,
        "compoundSplitErrors": compound_errs,
        "statusQuoErrors": sq,
        "crossTypeDuplicates": xt,
        "closingMetaLeakage": meta,
        "rootCauseReasonCodes": root_unique,
        "itemResults": item_results,
        "matrixCells": matrix_cells,
        "artifactAvailability": avail,
        "observabilityNote": (
            "Intermediate pipeline artifacts (raw/gate/lineage/validated) are NOT_AVAILABLE "
            "for these campaign runs; stage-level root causes beyond final-note matching "
            "are marked NOT_OBSERVABLE / UNCLASSIFIED_MISS."
        ),
    }


def matrix_markdown(runs: list[dict]) -> str:
    cols = ["Run", "D-01", "D-02", "A-01", "A-02", "A-03", "A-04", "A-05", "A-06", "A-07",
            "Cue 27 split", "Cue 51 split", "Status-quo 19/43", "Owner/date", "Duplicate", "Meta leakage", "Gate"]
    lines = ["# Hard-Gate Failure Matrix", "", "| " + " | ".join(cols) + " |", "|" + "|".join(["---"] * len(cols)) + "|"]
    for r in runs:
        c = r["matrixCells"]
        row = [
            r["runId"],
            c["D-01"], c["D-02"],
            c["A-01"], c["A-02"], c["A-03"], c["A-04"], c["A-05"], c["A-06"], c["A-07"],
            c["CUE27_SPLIT"], c["CUE51_SPLIT"], c["STATUS_QUO"], c["OWNER_DATE"], c["DUPLICATE"], c["META_LEAKAGE"],
            "PASS" if r["criticalGatePassed"] else "FAIL",
        ]
        lines.append("| " + " | ".join(row) + " |")
    lines += ["", "## Notes", "",
              "- Intermediate artifacts were NOT_AVAILABLE; MISS items are tagged UNCLASSIFIED_MISS / NOT_OBSERVABLE.",
              "- PASS on an action means text match in final note; owner/date issues appear in Owner/date column.",
              ""]
    return "\n".join(lines) + "\n"


def aggregate(runs: list[dict], baseline: str, commit: str) -> dict:
    subset = [r for r in runs if r["baseline"] == baseline]
    by_reason: Counter = Counter()
    by_gold: Counter = Counter()
    by_stage: Counter = Counter()
    for r in subset:
        for code in r["rootCauseReasonCodes"]:
            by_reason[code] += 1
        for m in r["missingGoldItems"]:
            by_gold[m["goldId"]] += 1
            by_stage["finalNote"] += 1
            by_stage["NOT_OBSERVABLE_UPSTREAM"] += 1
        for e in r["ownerErrors"]:
            by_gold[e["goldId"]] += 1
            by_reason["OWNER_MISBINDING"] += 1
        for e in r["dateErrors"]:
            by_gold[e["goldId"]] += 1
        for e in r["compoundSplitErrors"]:
            by_reason[e["reasonCode"]] += 1
        for e in r["statusQuoErrors"]:
            by_reason[e["reasonCode"]] += 1
        for e in r["crossTypeDuplicates"]:
            by_reason[e["reasonCode"]] += 1
        for e in r["closingMetaLeakage"]:
            by_reason[e["reasonCode"]] += 1
    return {
        "commit": commit,
        "runCount": len(subset),
        "criticalGatePassCount": sum(1 for r in subset if r["criticalGatePassed"]),
        "failureCountsByReasonCode": dict(by_reason.most_common()),
        "failureCountsByGoldItem": dict(by_gold.most_common()),
        "failureCountsByStage": dict(by_stage.most_common()),
    }


def compare_baselines(control: dict, candidate: dict, runs: list[dict]) -> tuple[list, list, list, list]:
    # miss rates by gold id
    gold_ids = sorted(set(control.get("failureCountsByGoldItem", {})) | set(candidate.get("failureCountsByGoldItem", {})))
    improvements, regressions = [], []
    for gid in gold_ids:
        b = control.get("failureCountsByGoldItem", {}).get(gid, 0)
        a = candidate.get("failureCountsByGoldItem", {}).get(gid, 0)
        if a < b:
            improvements.append({"goldId": gid, "controlMisses": b, "candidateMisses": a})
        elif a > b:
            regressions.append({"goldId": gid, "controlMisses": b, "candidateMisses": a})

    # systemic: highest miss items across both
    total_miss: Counter = Counter()
    for r in runs:
        for m in r["missingGoldItems"]:
            total_miss[m["goldId"]] += 1
    top = [{"goldId": g, "missCount": n, "note": "UNCLASSIFIED_MISS / NOT_OBSERVABLE (no intermediate artifacts)"}
           for g, n in total_miss.most_common(8)]

    # recommended fixes — only evidence-backed, max 2
    fixes = []
    # Action recall gap: open questions heavily missed
    oq_miss = sum(total_miss[g] for g in total_miss if g.startswith("Q-"))
    act_miss_a01 = total_miss.get("A-01", 0) + total_miss.get("A-02", 0) + total_miss.get("A-05", 0)
    if oq_miss >= 40:
        fixes.append({
            "id": "FIX-OQ-RECALL",
            "title": "Investigate open-question recall collapse",
            "evidence": f"{oq_miss} Q-* misses across 10 runs in final notes",
            "reasonCodes": ["UNCLASSIFIED_MISS", "NOT_OBSERVABLE"],
            "note": "Need raw-extraction/lineage artifacts before attributing to LLM vs post-filter",
        })
    if act_miss_a01 >= 8:
        fixes.append({
            "id": "FIX-ACTION-TESTPLAN-RECALL",
            "title": "Investigate missing test-plan / owner-less-guard actions (A-01/A-02/A-05)",
            "evidence": f"A-01+A-02+A-05 combined misses={act_miss_a01}/10 runs",
            "reasonCodes": ["UNCLASSIFIED_MISS", "NOT_OBSERVABLE"],
            "note": "Do not change production until stage lineage proves drop point",
        })
    # Owner misbinding observed in prior unit tests / if present in matrix
    owner_n = sum(len(r["ownerErrors"]) for r in runs)
    if owner_n and len(fixes) < 2:
        fixes.append({
            "id": "FIX-OWNER-BINDING",
            "title": "Fix compound-clause owner binding (speaker/parent leak)",
            "evidence": f"{owner_n} WRONG_OWNER observations in final notes across runs",
            "reasonCodes": ["OWNER_MISBINDING"],
        })
    return improvements, regressions, top, fixes[:2]


def summary_markdown(summary: dict) -> str:
    lines = ["# Root-Cause Summary (Phase 1B)", ""]
    for key in ("control", "candidate"):
        block = summary[key]
        lines += [f"## {key} ({block['commit']})", "",
                  f"- runs: {block['runCount']}",
                  f"- criticalGatePassCount: {block.get('criticalGatePassCount')}",
                  "", "### By reasonCode", ""]
        for k, v in (block.get("failureCountsByReasonCode") or {}).items():
            lines.append(f"- `{k}`: {v}")
        lines += ["", "### By gold item", ""]
        for k, v in (block.get("failureCountsByGoldItem") or {}).items():
            lines.append(f"- `{k}`: {v}")
        lines.append("")
    lines += ["## Candidate improvements", ""]
    for x in summary.get("candidateImprovements") or []:
        lines.append(f"- {x}")
    lines += ["", "## Candidate regressions", ""]
    for x in summary.get("candidateRegressions") or []:
        lines.append(f"- {x}")
    lines += ["", "## Top systemic failures", ""]
    for x in summary.get("topSystemicFailures") or []:
        lines.append(f"- {x}")
    lines += ["", "## Recommended next fixes (max 2)", ""]
    for x in summary.get("recommendedNextFixes") or []:
        lines.append(f"- **{x.get('id')}**: {x.get('title')} — {x.get('evidence')}")
    lines += ["", "## Observability", "",
              summary.get("observabilityNote", ""), ""]
    return "\n".join(lines) + "\n"


def analyze_tree(tree: Path, gold: dict) -> tuple[dict, dict]:
    runs = []
    for baseline, name, commit in (
        ("B", "B_HEAD_f9c699f", "f9c699f"),
        ("A", "A_CAND_472172a", "472172a"),
    ):
        for i in range(1, 6):
            run = f"run_{i:02d}"
            run_dir = tree / name / run
            note_path = run_dir / "final.note.json"
            if not note_path.exists():
                note_path = run_dir / "final-note.json"
            if not note_path.exists():
                continue
            note = load_json(note_path)
            avail = artifact_availability(run_dir)
            run_id = f"{name}_{run}"
            # Do not write into run dirs (immutable campaign artifacts)
            runs.append(analyze_run(run_id, baseline, commit, gold, note, avail))

    matrix = {
        "schemaVersion": "1.0",
        "phase": "1B",
        "fixture": gold.get("fixture"),
        "matchConfig": MATCH,
        "runs": [
            {k: v for k, v in r.items() if k != "itemResults"} | {"itemStatuses": {gid: ir["status"] for gid, ir in r["itemResults"].items()}}
            for r in runs
        ],
        "lifecycleByRun": {
            r["runId"]: {gid: ir["lifecycle"] for gid, ir in r["itemResults"].items()}
            for r in runs
        },
    }

    control = aggregate(runs, "B", "f9c699f")
    candidate = aggregate(runs, "A", "472172a")
    improvements, regressions, top, fixes = compare_baselines(control, candidate, runs)
    summary = {
        "schemaVersion": "1.0",
        "phase": "1B",
        "control": control,
        "candidate": candidate,
        "candidateImprovements": improvements,
        "candidateRegressions": regressions,
        "topSystemicFailures": top,
        "recommendedNextFixes": fixes,
        "observabilityNote": (
            "Campaign run packages contain final.note.json (+ meta) only. "
            "Therefore stage attribution for misses is NOT_OBSERVABLE; "
            "reasonCode UNCLASSIFIED_MISS is used when only final-note absence is proven."
        ),
        "assertionFailureCounts": {
            "control": Counter(a for r in runs if r["baseline"] == "B" for a in r["failedAssertions"]),
            "candidate": Counter(a for r in runs if r["baseline"] == "A" for a in r["failedAssertions"]),
        },
    }
    # convert Counters
    summary["assertionFailureCounts"] = {
        "control": dict(summary["assertionFailureCounts"]["control"]),
        "candidate": dict(summary["assertionFailureCounts"]["candidate"]),
    }
    return matrix, summary


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gold", type=Path, default=DEFAULT_GOLD)
    ap.add_argument("--tree-root", type=Path, default=DEFAULT_TREE)
    ap.add_argument("--out-dir", type=Path, default=None)
    args = ap.parse_args()
    out_dir = args.out_dir or args.tree_root
    gold = load_json(args.gold)
    matrix, summary = analyze_tree(args.tree_root, gold)

    # Deterministic JSON (sorted keys via consistent construction)
    (out_dir / "hard-gate-failure-matrix.json").write_text(
        json.dumps(matrix, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    (out_dir / "hard-gate-failure-matrix.md").write_text(
        matrix_markdown(matrix["runs"]), encoding="utf-8"
    )
    (out_dir / "root-cause-summary.json").write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    (out_dir / "root-cause-summary.md").write_text(
        summary_markdown(summary), encoding="utf-8"
    )
    print("wrote", out_dir / "hard-gate-failure-matrix.json")
    print("wrote", out_dir / "root-cause-summary.json")
    print("B gatePass", summary["control"].get("criticalGatePassCount"),
          "A gatePass", summary["candidate"].get("criticalGatePassCount"))
    print("top misses", summary["topSystemicFailures"][:5])


if __name__ == "__main__":
    main()
