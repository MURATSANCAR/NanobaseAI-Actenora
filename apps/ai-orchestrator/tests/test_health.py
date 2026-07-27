from __future__ import annotations

from unittest.mock import MagicMock

import httpx
from fastapi.testclient import TestClient

from actenora_orchestrator import main
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


def test_readiness_up_on_v1_models_2xx(monkeypatch) -> None:
    monkeypatch.setenv("LLM_BASE_URL", "http://127.0.0.1:11434")
    monkeypatch.delenv("QWEN_BASE_URL", raising=False)

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def get(self, url: str):
            assert url.endswith("/v1/models")
            response = MagicMock()
            response.status_code = 200
            return response

    monkeypatch.setattr(httpx, "Client", FakeClient)
    qwen = main.check_qwen()
    assert qwen["status"] == "UP"
    assert qwen["url"].endswith("/v1/models")


def test_readiness_degraded_on_404(monkeypatch) -> None:
    monkeypatch.setenv("LLM_BASE_URL", "http://127.0.0.1:11434")
    monkeypatch.delenv("QWEN_BASE_URL", raising=False)

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def get(self, url: str):
            response = MagicMock()
            response.status_code = 404
            return response

    monkeypatch.setattr(httpx, "Client", FakeClient)
    qwen = main.check_qwen()
    assert qwen["status"] == "DEGRADED"
    assert "404" in qwen["detail"]
