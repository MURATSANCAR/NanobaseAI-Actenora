from __future__ import annotations

import json
import re
from datetime import UTC, datetime
from typing import Any

__version__ = "0.1.0"

REDACTED = "[REDACTED]"

PIPELINE_STAGES = (
    "MeetingDiscovered",
    "MeetingEnded",
    "TranscriptFetched",
    "TranscriptNormalized",
    "AiJobRouted",
    "InferenceCompleted",
    "ValidationCompleted",
    "NoteDrafted",
    "Approved",
    "Rendered",
    "Delivered",
)

METRIC_NAMES = {
    "meeting_count": "actenora.meeting.count",
    "transcript_pending_age": "actenora.transcript.pending_age_seconds",
    "ai_queue_depth": "actenora.ai.queue_depth",
    "route_decision": "actenora.ai.route_decision",
    "queue_wait": "actenora.queue.wait_seconds",
    "inference_duration": "actenora.inference.duration_ms",
    "tokens": "actenora.inference.tokens",
    "invalid_json": "actenora.validation.invalid_json",
    "evidence_failure": "actenora.validation.evidence_failure",
    "approval_duration": "actenora.approval.duration_ms",
    "render_duration": "actenora.render.duration_ms",
    "mail_failures": "actenora.delivery.mail_failures",
    "dlq": "actenora.messaging.dlq_depth",
    "tenant_throughput": "actenora.tenant.throughput",
    "deployment_health": "actenora.deployment.health",
}

_BLOCKED = re.compile(
    r"^(password|passwd|secret|token|access[_-]?token|refresh[_-]?token|api[_-]?key|"
    r"authorization|auth|bearer|client[_-]?secret|transcript|transcript[_-]?text|"
    r"raw[_-]?transcript|prompt|completion|response[_-]?body|email|ssn|credit[_-]?card)$",
    re.IGNORECASE,
)
_BEARER = re.compile(r"(bearer\s+)[a-z0-9._\-]+", re.IGNORECASE)
_API_KEY = re.compile(r"(api[_-]?key\s*[:=]\s*)[^\s\"']+", re.IGNORECASE)
_JWT = re.compile(r"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b")
_EMAIL = re.compile(r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}")


def is_blocked_key(key: str) -> bool:
    if _BLOCKED.match(key):
        return True
    normalized = key.replace("-", "").replace("_", "").lower()
    return (
        "transcript" in normalized
        or "password" in normalized
        or "secret" in normalized
        or "apikey" in normalized
        or normalized.endswith("token")
    )


def redact_value(key: str, value: Any) -> Any:
    if value is None:
        return None
    if is_blocked_key(key):
        return REDACTED
    if isinstance(value, str):
        text = _BEARER.sub(rf"\1{REDACTED}", value)
        text = _API_KEY.sub(rf"\1{REDACTED}", text)
        text = _JWT.sub(REDACTED, text)
        text = _EMAIL.sub(REDACTED, text)
        return text
    return value


def redact_fields(**fields: Any) -> dict[str, Any]:
    return {k: redact_value(k, v) for k, v in fields.items()}


def format_log(service: str, level: str, message: str, **fields: Any) -> str:
    payload = {
        "ts": datetime.now(UTC).isoformat(),
        "service": service,
        "level": level,
        "message": message,
        **redact_fields(**fields),
    }
    return json.dumps(payload, separators=(",", ":"))
