from actenora_observability import __version__, format_log


def test_format_log_contains_service() -> None:
    line = format_log("ai-orchestrator", "INFO", "boot", port=8000)
    assert "ai-orchestrator" in line
    assert "8000" in line
    assert __version__ == "0.1.0"
