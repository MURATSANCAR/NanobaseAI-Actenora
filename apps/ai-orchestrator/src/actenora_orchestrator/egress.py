"""FAZ 27 — AI network egress deny: block public LLM hosts; allow local/private only."""

from __future__ import annotations

import ipaddress
import os
from urllib.parse import urlparse

# Public / hosted LLM endpoints that must never be reached in default profiles.
DENIED_HOST_SUFFIXES: tuple[str, ...] = (
    "api.openai.com",
    "openai.com",
    "api.anthropic.com",
    "anthropic.com",
    "generativelanguage.googleapis.com",
    "api.cohere.ai",
    "api.mistral.ai",
    "api.groq.com",
    "api.together.xyz",
    "api.fireworks.ai",
    "bedrock-runtime",
    "openai.azure.com",
)


class AiEgressDeniedError(RuntimeError):
    def __init__(self, host: str) -> None:
        super().__init__(f"AI egress denied for host={host}")
        self.host = host
        self.code = "AI_EGRESS_DENIED"


def _env_allowlist() -> set[str]:
    raw = os.environ.get("ACTENORA_AI_EGRESS_ALLOWLIST", "").strip()
    if not raw:
        return set()
    return {part.strip().lower() for part in raw.split(",") if part.strip()}


def _is_private_or_loopback(host: str) -> bool:
    try:
        ip = ipaddress.ip_address(host)
        return bool(ip.is_private or ip.is_loopback or ip.is_link_local)
    except ValueError:
        return False


def _is_local_hostname(host: str) -> bool:
    lowered = host.lower()
    return (
        lowered in {"localhost", "host.docker.internal"}
        or lowered.endswith((".local", ".internal"))
        or _is_private_or_loopback(lowered)
    )


def _matches_denied(host: str) -> bool:
    lowered = host.lower()
    for suffix in DENIED_HOST_SUFFIXES:
        if lowered == suffix or lowered.endswith("." + suffix) or suffix in lowered:
            return True
    return False


def assert_ai_egress_allowed(url: str) -> None:
    """Raise AiEgressDeniedError when URL targets a denied public LLM host."""
    if not url or not url.strip():
        raise AiEgressDeniedError("<empty>")

    parsed = urlparse(url.strip())
    host = parsed.hostname
    if not host:
        raise AiEgressDeniedError("<missing-host>")

    allowlist = _env_allowlist()
    if host.lower() in allowlist:
        return

    if _matches_denied(host):
        raise AiEgressDeniedError(host)

    # Default profile: only local/private endpoints (or explicit allowlist).
    deny_public = os.environ.get("ACTENORA_AI_EGRESS_DENY_PUBLIC", "true").lower() in {
        "1",
        "true",
        "yes",
    }
    if deny_public and not _is_local_hostname(host) and host.lower() not in allowlist:
        raise AiEgressDeniedError(host)
