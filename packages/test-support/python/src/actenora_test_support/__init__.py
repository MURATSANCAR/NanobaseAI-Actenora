from __future__ import annotations

from typing import TypeVar

__version__ = "0.1.0"

T = TypeVar("T")


def assert_defined(value: T | None, label: str = "value") -> T:
    if value is None:
        raise AssertionError(f"{label} must be defined")
    return value
