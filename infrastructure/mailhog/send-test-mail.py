#!/usr/bin/env python3
"""Send a one-shot SMTP message to MailHog for local delivery verification (FAZ 2)."""

from __future__ import annotations

import os
import smtplib
import sys
import time
from email.message import EmailMessage


def main() -> int:
    host = os.environ.get("MAILHOG_SMTP_HOST", "mailhog")
    port = int(os.environ.get("MAILHOG_SMTP_PORT", "1025"))
    sender = os.environ.get("MAIL_FROM", "actenora-local@example.test")
    recipient = os.environ.get("MAIL_TO", "developer@example.test")
    subject = os.environ.get("MAIL_SUBJECT", "Actenora FAZ-2 MailHog delivery test")

    msg = EmailMessage()
    msg["From"] = sender
    msg["To"] = recipient
    msg["Subject"] = subject
    msg.set_content(
        "This message confirms local SMTP delivery via MailHog.\n"
        "FAZ-2 acceptance: MailHog has received a test mail.\n"
    )

    last_error: Exception | None = None
    for attempt in range(1, 31):
        try:
            with smtplib.SMTP(host, port, timeout=5) as smtp:
                smtp.send_message(msg)
            print(f"Sent test mail to {recipient} via {host}:{port} (attempt {attempt})")
            return 0
        except Exception as exc:  # noqa: BLE001 — retry until MailHog accepts
            last_error = exc
            print(f"SMTP attempt {attempt} failed: {exc}", file=sys.stderr)
            time.sleep(1)

    print(f"Failed to send test mail: {last_error}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
