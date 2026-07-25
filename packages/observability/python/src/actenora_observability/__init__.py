from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any

__version__ = "0.1.0"


def format_log(service: str, level: str, message: str, **fields: Any) -> str:
    payload = {
        "ts": datetime.now(timezone.utc).isoformat(),
        "service": service,
        "level": level,
        "message": message,
        **fields,
    }
    return json.dumps(payload, separators=(",", ":"))
