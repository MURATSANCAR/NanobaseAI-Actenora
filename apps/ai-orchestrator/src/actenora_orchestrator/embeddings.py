"""On-prem multilingual embeddings.

The sentence-transformers model is baked into the Docker image at build time, so at runtime
NO public egress happens — embeddings stay fully local (consistent with the BDDK / on-prem
requirement that data never leaves the bank's servers). CPU inference; small model by default.
"""

from __future__ import annotations

import os
from functools import lru_cache
from typing import List

# Small, strong multilingual model (Turkish included), CPU-friendly (~118M params).
MODEL_NAME = os.environ.get("EMBEDDING_MODEL", "intfloat/multilingual-e5-small")


@lru_cache(maxsize=1)
def _model():
    # Imported lazily so the module (and its unit tests) load without torch present.
    from sentence_transformers import SentenceTransformer

    return SentenceTransformer(MODEL_NAME)


def _prepare(text: str) -> str:
    # e5 models expect an instruction prefix; "passage:" is the retrieval-corpus convention.
    stripped = text.strip()
    if stripped.startswith(("query:", "passage:")):
        return stripped
    return f"passage: {stripped}"


def embed(texts: List[str]) -> List[List[float]]:
    """Return L2-normalized embedding vectors for each input text."""
    if not texts:
        return []
    vectors = _model().encode(
        [_prepare(t) for t in texts],
        normalize_embeddings=True,
        convert_to_numpy=True,
        batch_size=int(os.environ.get("EMBEDDING_BATCH_SIZE", "32")),
    )
    return vectors.tolist()


def embed_one(text: str) -> List[float]:
    return embed([text])[0]


def model_name() -> str:
    return MODEL_NAME
