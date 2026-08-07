"""Embedding-based semantic chunking.

Keeps topically-coherent transcript segments together instead of cutting on a fixed token
count. A chunk boundary is placed where the semantic distance between adjacent segments spikes
(a topic shift) OR when the running token budget would be exceeded. Segment boundaries are
always respected (a segment is never split), and a minimum size prevents tiny fragments.
"""

from __future__ import annotations

from typing import Any, Callable, Dict, List, Optional

Segment = Dict[str, Any]
Embedder = Callable[[List[str]], List[List[float]]]


def _approx_tokens(text: str) -> int:
    return max(1, (len(text) + 3) // 4)


def _cosine(a: List[float], b: List[float]) -> float:
    # Vectors are L2-normalized by the embedder, so dot product == cosine similarity.
    return sum(x * y for x, y in zip(a, b))


def _percentile(values: List[float], pct: float) -> float:
    if not values:
        return 1.0
    ordered = sorted(values)
    k = (len(ordered) - 1) * (pct / 100.0)
    lo = int(k)
    hi = min(lo + 1, len(ordered) - 1)
    frac = k - lo
    return ordered[lo] * (1 - frac) + ordered[hi] * frac


def semantic_chunk(
    segments: List[Segment],
    *,
    max_tokens: int = 3500,
    min_tokens: int = 800,
    breakpoint_percentile: float = 90.0,
    embedder: Optional[Embedder] = None,
) -> List[Dict[str, Any]]:
    """Group segments into semantically-coherent, size-capped chunks.

    Each segment must be a dict with at least ``text`` (and optionally ``id``). Returns a list of
    chunks: ``{segment_ids, text, num_segments, approx_tokens}``.
    """
    segs = [s for s in segments if str(s.get("text", "")).strip()]
    if not segs:
        return []
    if embedder is None:
        from actenora_orchestrator.embeddings import embed as embedder  # lazy import

    vectors = embedder([str(s["text"]) for s in segs])
    distances = [
        1.0 - _cosine(vectors[i], vectors[i + 1]) for i in range(len(segs) - 1)
    ]
    threshold = _percentile(distances, breakpoint_percentile) if distances else 1.0

    chunks: List[Dict[str, Any]] = []
    current: List[Segment] = []
    current_tokens = 0

    for i, seg in enumerate(segs):
        current.append(seg)
        current_tokens += _approx_tokens(str(seg["text"]))

        is_last = i == len(segs) - 1
        boundary = is_last
        if not is_last:
            next_tokens = _approx_tokens(str(segs[i + 1]["text"]))
            over_budget = current_tokens + next_tokens > max_tokens
            topic_shift = distances[i] >= threshold and current_tokens >= min_tokens
            boundary = over_budget or topic_shift

        if boundary:
            chunks.append(
                {
                    "segment_ids": [s.get("id") for s in current],
                    "text": "\n".join(str(s["text"]) for s in current),
                    "num_segments": len(current),
                    "approx_tokens": current_tokens,
                }
            )
            current = []
            current_tokens = 0

    return chunks
