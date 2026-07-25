"""Actenora AI Orchestrator — health contract and Qwen readiness (FAZ 2 / FAZ 27)."""

from __future__ import annotations

import os
from enum import Enum
from typing import Any

import httpx
from actenora_observability import format_log
from fastapi import FastAPI, Response
from fastapi.responses import JSONResponse

from actenora_orchestrator.egress import AiEgressDeniedError, assert_ai_egress_allowed

app = FastAPI(title="Actenora AI Orchestrator", version="0.1.0")


class HealthStatus(str, Enum):
    UP = "UP"
    DOWN = "DOWN"
    DEGRADED = "DEGRADED"


def _env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


def qwen_base_url() -> str:
    """Prefer QWEN_BASE_URL; fall back to LLM_BASE_URL for local OpenAI-compatible runtimes."""
    return _env("QWEN_BASE_URL") or _env("LLM_BASE_URL")


def check_qwen() -> dict[str, Any]:
    base = qwen_base_url()
    if not base:
        return {
            "status": HealthStatus.DEGRADED.value,
            "detail": "QWEN_BASE_URL / LLM_BASE_URL not configured",
        }

    try:
        assert_ai_egress_allowed(base)
    except AiEgressDeniedError as exc:
        return {
            "status": HealthStatus.DOWN.value,
            "detail": str(exc),
            "code": exc.code,
            "url": base,
        }

    url = base.rstrip("/") + "/models"
    timeout = float(_env("QWEN_HEALTH_TIMEOUT_SECONDS", "2"))
    try:
        with httpx.Client(timeout=timeout) as client:
            response = client.get(url)
        if response.status_code < 500:
            return {"status": HealthStatus.UP.value, "detail": f"reachable ({response.status_code})", "url": url}
        return {
            "status": HealthStatus.DEGRADED.value,
            "detail": f"upstream status {response.status_code}",
            "url": url,
        }
    except Exception as exc:  # noqa: BLE001 — readiness must degrade, not crash
        return {
            "status": HealthStatus.DEGRADED.value,
            "detail": f"unreachable: {exc}",
            "url": url,
        }


def overall_health() -> dict[str, Any]:
    qwen = check_qwen()
    return {
        "status": HealthStatus.UP.value,
        "service": "ai-orchestrator",
        "components": {"qwen": qwen},
    }


def readiness_payload() -> tuple[dict[str, Any], int]:
    qwen = check_qwen()
    qwen_status = qwen["status"]
    if qwen_status == HealthStatus.UP.value:
        body = {
            "status": HealthStatus.UP.value,
            "service": "ai-orchestrator",
            "components": {"qwen": qwen},
        }
        return body, 200

    body = {
        "status": HealthStatus.DEGRADED.value,
        "service": "ai-orchestrator",
        "components": {"qwen": qwen},
    }
    # HTTP 200 with DEGRADED — process is alive; capacity is reduced (FAZ 2 contract).
    return body, 200


@app.get("/actuator/health")
def actuator_health() -> dict[str, Any]:
    print(format_log("ai-orchestrator", "INFO", "health-check"))
    return overall_health()


@app.get("/actuator/health/liveness")
def actuator_liveness() -> dict[str, Any]:
    return {"status": HealthStatus.UP.value}


@app.get("/actuator/health/readiness")
def actuator_readiness(response: Response) -> JSONResponse:
    body, status_code = readiness_payload()
    response.status_code = status_code
    return JSONResponse(content=body, status_code=status_code)


@app.get("/health")
def health_alias() -> dict[str, Any]:
    """Backward-compatible alias used by scripts/run-local."""
    return overall_health()


@app.get("/health/live")
def health_live_alias() -> dict[str, Any]:
    return {"status": HealthStatus.UP.value}


@app.get("/health/ready")
def health_ready_alias() -> JSONResponse:
    body, status_code = readiness_payload()
    return JSONResponse(content=body, status_code=status_code)
