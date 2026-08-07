# syntax=docker/dockerfile:1.7

FROM python:3.12-slim-bookworm AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system actenora \
    && useradd --system --gid actenora --create-home actenora

WORKDIR /app

COPY apps/ai-orchestrator/pyproject.toml /app/apps/ai-orchestrator/pyproject.toml
COPY apps/ai-orchestrator/src /app/apps/ai-orchestrator/src
COPY packages/observability/python /app/packages/observability/python
COPY packages/test-support/python /app/packages/test-support/python
COPY pyproject.toml /app/pyproject.toml

RUN pip install --no-cache-dir \
      "/app/packages/observability/python" \
      "/app/packages/test-support/python" \
      "fastapi>=0.115.12" \
      "uvicorn[standard]>=0.34.2" \
      "httpx>=0.28.1" \
      "pydantic>=2.7" \
    && pip install --no-cache-dir --no-deps "/app/apps/ai-orchestrator"

# On-prem embeddings: install the CPU-only torch wheel (the default index pulls the multi-GB
# CUDA build) then sentence-transformers, and bake the embedding model into the image so runtime
# needs no public egress (data stays on-prem — BDDK requirement).
ENV EMBEDDING_MODEL=intfloat/multilingual-e5-small \
    HF_HOME=/app/hf-cache
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu "torch>=2.2,<3" \
    && pip install --no-cache-dir "sentence-transformers>=3.0,<4" "numpy>=1.26" \
    && python -c "import os; from sentence_transformers import SentenceTransformer; SentenceTransformer(os.environ['EMBEDDING_MODEL'])" \
    && chown -R actenora:actenora /app/hf-cache

ENV PYTHONPATH=/app/apps/ai-orchestrator/src \
    AI_ORCHESTRATOR_HOST=0.0.0.0 \
    AI_ORCHESTRATOR_PORT=8000 \
    ACTENORA_ENV=local \
    HF_HUB_OFFLINE=1 \
    TRANSFORMERS_OFFLINE=1

USER actenora
EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=10 \
  CMD curl -fsS http://127.0.0.1:8000/actuator/health/liveness | grep -q UP

CMD ["sh", "-c", "uvicorn actenora_orchestrator.main:app --host ${AI_ORCHESTRATOR_HOST:-0.0.0.0} --port ${AI_ORCHESTRATOR_PORT:-8000}"]
