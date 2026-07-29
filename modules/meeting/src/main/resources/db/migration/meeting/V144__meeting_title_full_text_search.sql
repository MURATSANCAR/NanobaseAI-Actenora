ALTER TABLE meeting.meeting_occurrences
    ADD COLUMN IF NOT EXISTS title_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(title, ''))) STORED;

CREATE INDEX IF NOT EXISTS idx_meeting_occurrences_title_fts
    ON meeting.meeting_occurrences
    USING GIN (title_tsv);
