from __future__ import annotations

from typing import List

from actenora_orchestrator.semantic_chunk import _percentile, semantic_chunk


def _fake_embedder(vectors_by_text: dict[str, List[float]]):
    """Return a deterministic embedder that maps known texts to fixed unit vectors."""

    def embed(texts: List[str]) -> List[List[float]]:
        return [vectors_by_text[t] for t in texts]

    return embed


def test_empty_input_returns_no_chunks() -> None:
    assert semantic_chunk([], embedder=lambda t: []) == []


def test_blank_segments_are_dropped() -> None:
    embedder = _fake_embedder({"real": [1.0, 0.0]})
    chunks = semantic_chunk(
        [{"id": "a", "text": "   "}, {"id": "b", "text": "real"}],
        embedder=embedder,
    )
    assert len(chunks) == 1
    assert chunks[0]["segment_ids"] == ["b"]


def test_topic_shift_creates_boundary() -> None:
    # Two coherent segments (same vector) then a sharply different one.
    long_a = "alpha " * 300  # ~ enough tokens to clear min_tokens
    long_b = "alpha " * 300
    long_c = "gamma " * 300
    embedder = _fake_embedder(
        {long_a: [1.0, 0.0], long_b: [1.0, 0.0], long_c: [0.0, 1.0]}
    )
    chunks = semantic_chunk(
        [
            {"id": "1", "text": long_a},
            {"id": "2", "text": long_b},
            {"id": "3", "text": long_c},
        ],
        min_tokens=100,
        max_tokens=100000,
        breakpoint_percentile=50.0,
        embedder=embedder,
    )
    # The distance 1->2 is 0 and 2->3 is 1.0; the boundary lands before segment 3.
    assert len(chunks) == 2
    assert chunks[0]["segment_ids"] == ["1", "2"]
    assert chunks[1]["segment_ids"] == ["3"]


def test_token_budget_forces_boundary_even_without_topic_shift() -> None:
    # All identical vectors (no topic shift), but max_tokens forces splitting.
    text = "word " * 400  # ~500 chars -> ~125 approx tokens
    embedder = _fake_embedder({text: [1.0, 0.0]})
    segments = [{"id": str(i), "text": text} for i in range(6)]
    chunks = semantic_chunk(
        segments, max_tokens=300, min_tokens=0, embedder=embedder
    )
    assert len(chunks) >= 2
    for c in chunks:
        assert c["approx_tokens"] <= 300 or c["num_segments"] == 1


def test_single_segment_is_one_chunk() -> None:
    embedder = _fake_embedder({"only": [1.0, 0.0]})
    chunks = semantic_chunk([{"id": "x", "text": "only"}], embedder=embedder)
    assert len(chunks) == 1
    assert chunks[0]["num_segments"] == 1


def test_percentile_interpolates() -> None:
    assert _percentile([0.0, 1.0], 50.0) == 0.5
    assert _percentile([0.0, 10.0], 90.0) == 9.0
    assert _percentile([], 90.0) == 1.0
