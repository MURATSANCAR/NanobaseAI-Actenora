"""On-prem multilingual embeddings.

The sentence-transformers model is baked into the Docker image at build time, so at runtime
NO public egress happens — embeddings stay fully local (consistent with the BDDK / on-prem
requirement that data never leaves the bank's servers). CPU inference; small model by default.
"""

from __future__ import annotations

import os
from functools import lru_cache
from typing import List

# Strong multilingual model (Turkish included). Default BAAI/bge-m3 (1024-dim, 8192 ctx, MIT).
MODEL_NAME = os.environ.get("EMBEDDING_MODEL", "BAAI/bge-m3")


@lru_cache(maxsize=1)
def _model():
    # Imported lazily so the module (and its unit tests) load without torch present.
    from sentence_transformers import SentenceTransformer

    return SentenceTransformer(MODEL_NAME)


# Only the e5 family expects a "passage:"/"query:" instruction prefix. bge-m3, gte, etc. take
# raw text — prefixing them would degrade quality, so the prefix is model-conditional.
_NEEDS_E5_PREFIX = "e5" in MODEL_NAME.lower()


def _prepare(text: str) -> str:
    stripped = text.strip()
    if not _NEEDS_E5_PREFIX:
        return stripped
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
