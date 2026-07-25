import pytest

from actenora_test_support import assert_defined


def test_assert_defined_ok() -> None:
    assert assert_defined("x") == "x"


def test_assert_defined_none() -> None:
    with pytest.raises(AssertionError):
        assert_defined(None, "x")
