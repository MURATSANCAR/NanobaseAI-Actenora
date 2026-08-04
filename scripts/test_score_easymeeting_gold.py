#!/usr/bin/env python3
"""Unit tests for score-easymeeting-gold.py — deterministic, no LLM."""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import importlib.util

SPEC = importlib.util.spec_from_file_location(
    "score_easymeeting_gold", ROOT / "scripts" / "score-easymeeting-gold.py"
)
scorer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(scorer)

GOLD_PATH = ROOT / "modules/ai-processing/src/test/resources/aiprocessing/eval/gold/01_15dk_daily_standup.gold.v1.json"


def load_gold():
    return json.loads(GOLD_PATH.read_text())


def note_with(**buckets):
    base = {
        "decisions": [],
        "actionItems": [],
        "risks": [],
        "importantFacts": [],
        "openQuestions": [],
        "commitments": [],
    }
    base.update(buckets)
    return base


class ScorerDeterminismTest(unittest.TestCase):
    def test_same_input_same_output(self):
        gold = load_gold()
        note = note_with(
            decisions=[
                {"text": "Paralel refresh çağrıları tek promise üzerinde birleştirilecek."},
                {"text": "Yeni e-posta gönderimlerinde UTF-8 başlığı zorunlu olacak."},
            ],
            actionItems=[
                {"text": "owner veya tarihi bulunmayan kayıtların otomatik görev oluşturmadığını kontrol edecek", "owner": "Burak"},
                {"text": "oturum yenileme test planına timeout, retry, yetkisiz erişim senaryolarını ekleyecek", "owner": "Burak"},
                {"text": "paralel refresh çağrılarını tek promise üzerinde birleştiren düzeltmeyi uygulayacak", "owner": "Selin", "dueDate": "bugün 16.00"},
                {"text": "oturum yenileme akışına correlation ID ekleyecek", "owner": "Can"},
                {"text": "e-posta karakter bozulması test planına timeout retry senaryolarını ekleyecek", "owner": "Burak"},
                {"text": "yeni gönderimlerde kullanılan e-posta başlığını UTF-8 zorunluluğuna göre düzeltecek", "owner": "Can"},
                {"text": "Outlook ve Apple Mail regresyon testlerini tamamlayacak", "owner": "Burak", "dueDate": "yarın öğlene kadar"},
            ],
            risks=[
                {"text": "Tek promise değişikliğinin bekleyen isteklerin kilitlenmesine neden olması"},
                {"text": "E-posta başlık değişikliğinin eski şablonları geriye dönük etkilemesi"},
            ],
        )
        a = scorer.score_note(gold, note)
        b = scorer.score_note(gold, note)
        self.assertEqual(a, b)


class ScorerStatusQuoFpTest(unittest.TestCase):
    def test_status_quo_decision_detected(self):
        gold = load_gold()
        note = note_with(
            decisions=[
                {"text": "Token süresini şimdilik değiştirmiyoruz; bu kalıcı ürün kararı değil."},
            ]
        )
        score = scorer.score_note(gold, note)
        self.assertGreaterEqual(score["statusQuoFalsePositiveCount"], 1)
        self.assertIn("STATUS_QUO_FALSE_POSITIVE", score["failureReasonCodes"])


class ScorerOwnerMisbindingTest(unittest.TestCase):
    def test_owner_misbinding_counted(self):
        gold = load_gold()
        note = note_with(
            actionItems=[
                {
                    "text": "paralel refresh çağrılarını tek promise üzerinde birleştiren düzeltmeyi uygulayacak",
                    "owner": "Can",
                }
            ]
        )
        score = scorer.score_note(gold, note)
        if score["actionItem"]["truePositive"] >= 1:
            self.assertGreaterEqual(score["ownerMisBindingCount"], 1)
            self.assertIn("OWNER_MISBINDING", score["failureReasonCodes"])


class ScorerDateCrossoverTest(unittest.TestCase):
    def test_date_crossover_detected(self):
        gold = load_gold()
        note = note_with(
            actionItems=[
                {
                    "text": "paralel refresh çağrılarını tek promise üzerinde birleştiren düzeltmeyi uygulayacak",
                    "owner": "Selin",
                    "dueDate": "bugün 16.00",
                },
                {
                    "text": "oturum yenileme akışına correlation ID ekleyecek",
                    "owner": "Can",
                    "dueDate": "bugün 16.00",
                },
            ]
        )
        score = scorer.score_note(gold, note)
        if score["actionItem"]["truePositive"] >= 2:
            self.assertGreaterEqual(score["dateCrossoverCount"], 1)


class ScorerCompoundSplitTest(unittest.TestCase):
    def test_compound_split_accuracy(self):
        gold = load_gold()
        note = note_with(
            actionItems=[
                {
                    "text": "paralel refresh çağrılarını tek promise üzerinde birleştiren düzeltmeyi uygulayacak",
                    "owner": "Selin",
                    "dueDate": "bugün 16.00",
                },
                {
                    "text": "oturum yenileme akışına correlation ID ekleyecek",
                    "owner": "Can",
                },
                {
                    "text": "yeni gönderimlerde kullanılan e-posta başlığını UTF-8 zorunluluğuna göre düzeltecek",
                    "owner": "Can",
                },
                {
                    "text": "Outlook ve Apple Mail regresyon testlerini tamamlayacak",
                    "owner": "Burak",
                    "dueDate": "yarın öğlene kadar",
                },
            ]
        )
        score = scorer.score_note(gold, note)
        self.assertEqual(score["compoundSplitAccuracy"], 1.0)


class ScorerCrossTypeDuplicateTest(unittest.TestCase):
    def test_cross_type_duplicate_rate_field_present(self):
        gold = load_gold()
        note = note_with(
            decisions=[{"text": "Paralel refresh çağrıları tek promise üzerinde birleştirilecek."}],
            actionItems=[
                {
                    "text": "Paralel refresh çağrıları tek promise üzerinde birleştirilecek.",
                    "owner": "Selin",
                }
            ],
        )
        score = scorer.score_note(gold, note)
        self.assertIn("crossTypeDuplicateRate", score)
        self.assertIn("crossTypeDuplicateCount", score)


class StabilityAndChampionTest(unittest.TestCase):
    def test_stability_calculation(self):
        scores = [
            {"decision": {"criticalRecall": 1.0}, "actionItem": {"recall": 0.8}, "risk": {"recall": 1.0},
             "openQuestion": {"recall": 0.2}, "overallScore": 70.0, "criticalGatePassed": False},
            {"decision": {"criticalRecall": 1.0}, "actionItem": {"recall": 0.9}, "risk": {"recall": 1.0},
             "openQuestion": {"recall": 0.3}, "overallScore": 75.0, "criticalGatePassed": False},
        ]
        stab = scorer.stability(scores)
        self.assertEqual(stab["runCount"], 2)
        self.assertEqual(stab["criticalGatePassCount"], 0)
        self.assertAlmostEqual(stab["metricMean"]["actionItem.recall"], 0.85, places=4)

    def test_champion_eligibility_blocked_when_gate_low(self):
        b = {
            "criticalGatePassRate": 0.0,
            "metricMean": {"decision.criticalRecall": 1.0, "actionItem.recall": 0.7},
        }
        a = {
            "criticalGatePassRate": 0.2,
            "metricMean": {"decision.criticalRecall": 1.0, "actionItem.recall": 0.8},
        }
        cmp = scorer.compare(b, a)
        self.assertFalse(cmp["candidateChampionEligible"])


if __name__ == "__main__":
    unittest.main()
