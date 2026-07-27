-- Approved-note knowledge items for hybrid FTS + vector retrieval.
-- Embeddings are written only for human-approved structured items (never raw transcript).
-- Requires pgvector (CREATE EXTENSION vector) — use pgvector/pgvector:pg16 image.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS meetingintelligence.meeting_knowledge_item (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    note_id                 UUID NOT NULL,
    note_version_id         UUID NOT NULL,
    source_item_id          UUID NOT NULL,
    item_kind               VARCHAR(64) NOT NULL,
    content                 TEXT NOT NULL,
    embedding               vector(1024),
    content_tsv             tsvector GENERATED ALWAYS AS (
                                to_tsvector('simple', coalesce(content, ''))
                            ) STORED,
    created_at              TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, source_item_id, item_kind)
);

CREATE INDEX IF NOT EXISTS idx_meeting_knowledge_tenant_occurrence
    ON meetingintelligence.meeting_knowledge_item (tenant_id, meeting_occurrence_id);

CREATE INDEX IF NOT EXISTS idx_meeting_knowledge_content_tsv
    ON meetingintelligence.meeting_knowledge_item USING GIN (content_tsv);

CREATE INDEX IF NOT EXISTS idx_meeting_knowledge_embedding_hnsw
    ON meetingintelligence.meeting_knowledge_item
    USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;
