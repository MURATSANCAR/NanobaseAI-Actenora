"""FAZ 27 — AI egress denial tests."""

from __future__ import annotations

import os

import pytest

from actenora_orchestrator.egress import AiEgressDeniedError, assert_ai_egress_allowed


def test_public_openai_egress_denied(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("ACTENORA_AI_EGRESS_ALLOWLIST", raising=False)
    with pytest.raises(AiEgressDeniedError) as exc:
        assert_ai_egress_allowed("https://api.openai.com/v1/chat/completions")
    assert exc.value.code == "AI_EGRESS_DENIED"


def test_anthropic_egress_denied(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("ACTENORA_AI_EGRESS_ALLOWLIST", raising=False)
    with pytest.raises(AiEgressDeniedError):
        assert_ai_egress_allowed("https://api.anthropic.com/v1/messages")


def test_local_llm_egress_allowed(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("ACTENORA_AI_EGRESS_ALLOWLIST", raising=False)
    assert_ai_egress_allowed("http://127.0.0.1:11434/v1")
    assert_ai_egress_allowed("http://localhost:8001/v1")


def test_explicit_allowlist_permits_host(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ACTENORA_AI_EGRESS_ALLOWLIST", "llm.corp.example")
    assert_ai_egress_allowed("https://llm.corp.example/v1")
