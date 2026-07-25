from __future__ import annotations

__version__ = "0.1.0"


def assert_defined[T](value: T | None, label: str = "value") -> T:
    if value is None:
        raise AssertionError(f"{label} must be defined")
    return value
