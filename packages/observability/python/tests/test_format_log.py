import json

from actenora_observability import (
    METRIC_NAMES,
    PIPELINE_STAGES,
    REDACTED,
    __version__,
    format_log,
)


def test_format_log_contains_service() -> None:
    line = format_log("ai-orchestrator", "INFO", "boot", port=8000)
    assert "ai-orchestrator" in line
    assert "8000" in line
    assert __version__ == "0.1.0"


def test_secret_and_transcript_redaction() -> None:
    snippet = "verbatim board transcript never log"
    line = format_log(
        "ai-orchestrator",
        "INFO",
        "job",
        api_key="sk-secret",
        transcript=snippet,
        jobId="j-1",
    )
    assert "sk-secret" not in line
    assert snippet not in line
    assert REDACTED in line
    payload = json.loads(line)
    assert payload["jobId"] == "j-1"


def test_pipeline_and_metrics_constants() -> None:
    assert PIPELINE_STAGES[0] == "MeetingDiscovered"
    assert PIPELINE_STAGES[-1] == "Delivered"
    assert METRIC_NAMES["dlq"] == "actenora.messaging.dlq_depth"
