from __future__ import annotations

from fastapi import FastAPI

from actenora_observability import format_log

app = FastAPI(title="Actenora AI Orchestrator", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    print(format_log("ai-orchestrator", "INFO", "health-check"))
    return {"status": "UP", "service": "ai-orchestrator"}
