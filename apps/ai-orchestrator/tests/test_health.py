from fastapi.testclient import TestClient

from actenora_orchestrator.main import app
from actenora_test_support import assert_defined


def test_health() -> None:
    client = TestClient(app)
    response = client.get("/health")
    assert response.status_code == 200
    body = assert_defined(response.json())
    assert body["status"] == "UP"
    assert body["service"] == "ai-orchestrator"
