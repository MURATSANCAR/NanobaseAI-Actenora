from __future__ import annotations

import os

from fastapi.testclient import TestClient

from actenora_orchestrator.main import app


def test_liveness_up() -> None:
    client = TestClient(app)
    response = client.get("/actuator/health/liveness")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"


def test_readiness_degraded_without_qwen(monkeypatch) -> None:
    monkeypatch.delenv("QWEN_BASE_URL", raising=False)
    monkeypatch.delenv("LLM_BASE_URL", raising=False)
    client = TestClient(app)
    response = client.get("/actuator/health/readiness")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "DEGRADED"
    assert body["components"]["qwen"]["status"] == "DEGRADED"


def test_overall_health_up_when_qwen_missing(monkeypatch) -> None:
    monkeypatch.delenv("QWEN_BASE_URL", raising=False)
    monkeypatch.delenv("LLM_BASE_URL", raising=False)
    client = TestClient(app)
    response = client.get("/actuator/health")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"
