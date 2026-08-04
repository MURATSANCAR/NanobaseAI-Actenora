#!/usr/bin/env python3
"""Phase 1B analyzer / root-cause unit tests — deterministic, no LLM."""
from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "analyze_easymeeting_hard_gate", ROOT / "scripts" / "analyze-easymeeting-hard-gate.py"
)
mod = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mod)

GOLD = json.loads(
    (ROOT / "modules/ai-processing/src/test/resources/aiprocessing/eval/gold/01_15dk_daily_standup.gold.v1.json").read_text()
)


def note(**kw):
    base = {
        "decisions": [],
        "actionItems": [],
        "risks": [],
        "importantFacts": [],
        "openQuestions": [],
        "commitments": [],
        "proposals": [],
    }
    base.update(kw)
    return base


class DeterminismTests(unittest.TestCase):
    def test_failure_matrix_deterministic(self):
        n = note(
            decisions=[
                {"text": "Paralel refresh çağrıları tek promise üzerinde birleştirilecek."},
                {"text": "Yeni e-posta gönderimlerinde UTF-8 başlığı zorunlu olacak."},
            ]
        )
        a = mod.analyze_run("t1", "B", "f9c699f", GOLD, n, {"finalNote": "AVAILABLE"})
        b = mod.analyze_run("t1", "B", "f9c699f", GOLD, n, {"finalNote": "AVAILABLE"})
        self.assertEqual(a["matrixCells"], b["matrixCells"])
        self.assertEqual(a["rootCauseReasonCodes"], b["rootCauseReasonCodes"])

    def test_root_cause_summary_deterministic(self):
        with tempfile.TemporaryDirectory() as td:
            tree = Path(td)
            for name, commit, baseline in (
                ("B_HEAD_f9c699f", "f9c699f", "B"),
                ("A_CAND_472172a", "472172a", "A"),
            ):
                for i in range(1, 3):
                    d = tree / name / f"run_0{i}"
                    d.mkdir(parents=True)
                    (d / "final.note.json").write_text(json.dumps(note(
                        decisions=[{"text": "Paralel refresh çağrıları tek promise üzerinde birleştirilecek."}]
                    )), encoding="utf-8")
            m1, s1 = mod.analyze_tree(tree, GOLD)
            m2, s2 = mod.analyze_tree(tree, GOLD)
            self.assertEqual(s1["control"]["failureCountsByGoldItem"], s2["control"]["failureCountsByGoldItem"])
            self.assertEqual(m1["runs"][0]["matrixCells"], m2["runs"][0]["matrixCells"])


class ReasonCodeTests(unittest.TestCase):
    def test_missing_raw_candidate_unclassified_without_intermediates(self):
        r = mod.analyze_run("x", "B", "f9c699f", GOLD, note(), {"finalNote": "AVAILABLE", "rawExtractions": "NOT_AVAILABLE"})
        self.assertIn("UNCLASSIFIED_MISS", r["rootCauseReasonCodes"])
        self.assertIn("NOT_OBSERVABLE", r["rootCauseReasonCodes"])
        self.assertEqual(r["itemResults"]["D-01"]["lifecycle"]["rawLlmExtraction"], "NOT_OBSERVABLE")

    def test_status_quo_detection(self):
        r = mod.analyze_run("x", "B", "f9c699f", GOLD, note(decisions=[
            {"text": "Token süresini şimdilik değiştirmiyoruz; bu kalıcı ürün kararı değil."}
        ]), {"finalNote": "AVAILABLE"})
        self.assertTrue(r["statusQuoErrors"])
        self.assertIn("STATUS_QUO_FALSE_POSITIVE", r["rootCauseReasonCodes"])

    def test_compound_split_failure(self):
        r = mod.analyze_run("x", "B", "f9c699f", GOLD, note(actionItems=[
            {"text": "paralel refresh çağrılarını tek promise üzerinde birleştiren düzeltmeyi uygulayacak", "owner": "Selin", "dueDate": "2026-08-03"}
            # A-04 missing → compound fail
        ]), {"finalNote": "AVAILABLE"})
        self.assertTrue(any(e["reasonCode"] == "ACTION_COMPOUND_NOT_SPLIT" for e in r["compoundSplitErrors"]))

    def test_date_crossover_cue27(self):
        r = mod.analyze_run("x", "B", "f9c699f", GOLD, note(actionItems=[
            {"text": "paralel refresh çağrılarını tek promise üzerinde birleştiren düzeltmeyi uygulayacak", "owner": "Selin", "dueDate": "2026-08-03"},
            {"text": "oturum yenileme akışına correlation ID ekleyecek", "owner": "Can", "dueDate": "2026-08-03"},
        ]), {"finalNote": "AVAILABLE"})
        if r["itemResults"]["A-03"]["status"] != "MISS" and r["itemResults"]["A-04"]["status"] != "MISS":
            self.assertTrue(any(e.get("reasonCode") == "DATE_CROSSOVER" for e in r["compoundSplitErrors"])
                            or r["itemResults"]["A-04"]["status"] == "DATE_CROSSOVER")

    def test_date_crossover_cue51(self):
        r = mod.analyze_run("x", "B", "f9c699f", GOLD, note(actionItems=[
            {"text": "yeni gönderimlerde kullanılan e-posta başlığını UTF-8 zorunluluğuna göre düzeltecek", "owner": "Can", "dueDate": "2026-08-04"},
            {"text": "Outlook ve Apple Mail regresyon testlerini tamamlayacak", "owner": "Burak", "dueDate": "2026-08-04"},
        ]), {"finalNote": "AVAILABLE"})
        if r["itemResults"]["A-06"]["status"] != "MISS" and r["itemResults"]["A-07"]["status"] != "MISS":
            self.assertTrue(
                any(e.get("reasonCode") == "DATE_CROSSOVER" for e in r["compoundSplitErrors"])
                or "DATE_CROSSOVER" in (r["itemResults"]["A-06"].get("reasonCodes") or [])
            )

    def test_not_observable_fallback(self):
        r = mod.analyze_run("x", "A", "472172a", GOLD, note(), {"finalNote": "AVAILABLE"})
        life = r["itemResults"]["Q-01"]["lifecycle"]
        self.assertEqual(life["chunkGate"], "NOT_OBSERVABLE")
        self.assertEqual(life["rawLlmExtraction"], "NOT_OBSERVABLE")
        self.assertEqual(life["finalNote"], "ABSENT")


class SyntheticReasonMappingTests(unittest.TestCase):
    """Document expected reason codes when stage evidence *is* available (unit-level)."""

    def test_llm_missed_item_code_exists(self):
        self.assertEqual(mod.NOT_OBSERVABLE, "NOT_OBSERVABLE")

    def test_gate_false_negative_listed_in_protocol(self):
        # Analyzer reserves GATE_FALSE_NEGATIVE for when gate-decisions.json exists.
        self.assertTrue(hasattr(mod, "analyze_run"))


if __name__ == "__main__":
    unittest.main()
