# syntax=docker/dockerfile:1.7

FROM ghcr.io/astral-sh/uv:python3.12-bookworm AS build
WORKDIR /workspace
COPY pyproject.toml uv.lock ./
COPY packages/observability/python packages/observability/python
COPY packages/test-support/python packages/test-support/python
COPY apps/ai-orchestrator apps/ai-orchestrator
RUN uv sync --frozen --package actenora-orchestrator --no-dev

FROM python:3.12-slim-bookworm AS runtime
RUN groupadd --system actenora && useradd --system --gid actenora --create-home actenora
WORKDIR /app
COPY --from=build /workspace/.venv /app/.venv
COPY --from=build /workspace/apps/ai-orchestrator/src /app/src
COPY --from=build /workspace/packages/observability/python/src /app/packages/observability/src
COPY --from=build /workspace/packages/test-support/python/src /app/packages/test-support/src
ENV PATH="/app/.venv/bin:$PATH"
ENV PYTHONPATH="/app/src:/app/packages/observability/src:/app/packages/test-support/src"
USER actenora
EXPOSE 8000
CMD ["uvicorn", "actenora_orchestrator.main:app", "--host", "0.0.0.0", "--port", "8000"]
