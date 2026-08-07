"""Actenora AI Orchestrator — health contract and Qwen readiness (FAZ 2 / FAZ 27)."""

from __future__ import annotations

import os
from enum import Enum
from typing import Any

import httpx
from actenora_observability import format_log
from fastapi import FastAPI, HTTPException, Response
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

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

    # Prefer OpenAI-compatible /v1/models (llama-server, Ollama); fall back to bare /models.
    candidates = (
        base.rstrip("/") + "/v1/models",
        base.rstrip("/") + "/models",
    )
    timeout = float(_env("QWEN_HEALTH_TIMEOUT_SECONDS", "2"))
    last_url = candidates[0]
    last_detail = "unreachable"
    try:
        with httpx.Client(timeout=timeout) as client:
            for url in candidates:
                last_url = url
                try:
                    response = client.get(url)
                except Exception as exc:  # noqa: BLE001 — try next probe path
                    last_detail = f"unreachable: {exc}"
                    continue
                if 200 <= response.status_code < 300:
                    return {
                        "status": HealthStatus.UP.value,
                        "detail": f"reachable ({response.status_code})",
                        "url": url,
                    }
                last_detail = f"upstream status {response.status_code}"
        return {
            "status": HealthStatus.DEGRADED.value,
            "detail": last_detail,
            "url": last_url,
        }
    except Exception as exc:  # noqa: BLE001 — readiness must degrade, not crash
        return {
            "status": HealthStatus.DEGRADED.value,
            "detail": f"unreachable: {exc}",
            "url": last_url,
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


# ---------------------------------------------------------------------------
# Embeddings + semantic chunking (on-prem; model baked into the image at build)
# ---------------------------------------------------------------------------


class EmbedRequest(BaseModel):
    texts: list[str] = Field(..., min_length=1, description="Texts to embed")


class EmbedResponse(BaseModel):
    model: str
    dimension: int
    vectors: list[list[float]]


class ChunkSegment(BaseModel):
    id: str | None = None
    text: str


class SemanticChunkRequest(BaseModel):
    segments: list[ChunkSegment] = Field(..., min_length=1)
    max_tokens: int = Field(3500, ge=256, le=32768)
    min_tokens: int = Field(800, ge=0, le=32768)
    breakpoint_percentile: float = Field(90.0, ge=50.0, le=100.0)


class SemanticChunk(BaseModel):
    segment_ids: list[str | None]
    text: str
    num_segments: int
    approx_tokens: int


class SemanticChunkResponse(BaseModel):
    model: str
    num_chunks: int
    chunks: list[SemanticChunk]


@app.post("/embed", response_model=EmbedResponse)
def embed_endpoint(request: EmbedRequest) -> EmbedResponse:
    from actenora_orchestrator import embeddings

    try:
        vectors = embeddings.embed(request.texts)
    except Exception as exc:  # noqa: BLE001 — surface a clean 503 instead of a stack trace
        print(format_log("ai-orchestrator", "ERROR", f"embed-failed: {exc}"))
        raise HTTPException(status_code=503, detail=f"embedding backend error: {exc}") from exc
    dimension = len(vectors[0]) if vectors else 0
    return EmbedResponse(model=embeddings.model_name(), dimension=dimension, vectors=vectors)


@app.post("/semantic-chunk", response_model=SemanticChunkResponse)
def semantic_chunk_endpoint(request: SemanticChunkRequest) -> SemanticChunkResponse:
    from actenora_orchestrator import embeddings
    from actenora_orchestrator.semantic_chunk import semantic_chunk

    try:
        chunks = semantic_chunk(
            [seg.model_dump() for seg in request.segments],
            max_tokens=request.max_tokens,
            min_tokens=request.min_tokens,
            breakpoint_percentile=request.breakpoint_percentile,
        )
    except Exception as exc:  # noqa: BLE001 — surface a clean 503 instead of a stack trace
        print(format_log("ai-orchestrator", "ERROR", f"semantic-chunk-failed: {exc}"))
        raise HTTPException(status_code=503, detail=f"chunking backend error: {exc}") from exc
    return SemanticChunkResponse(
        model=embeddings.model_name(),
        num_chunks=len(chunks),
        chunks=[SemanticChunk(**c) for c in chunks],
    )
