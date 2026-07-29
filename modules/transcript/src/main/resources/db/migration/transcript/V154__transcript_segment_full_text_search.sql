ALTER TABLE transcript.transcript_segments
    ADD COLUMN IF NOT EXISTS content_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED;

CREATE INDEX IF NOT EXISTS idx_transcript_segments_content_fts
    ON transcript.transcript_segments
    USING GIN (content_tsv);
