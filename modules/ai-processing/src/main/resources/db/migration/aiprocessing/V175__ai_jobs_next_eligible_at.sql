-- FAZ reliability: retry backoff gate for re-queued AI jobs
ALTER TABLE aiprocessing.ai_jobs
    ADD COLUMN IF NOT EXISTS next_eligible_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_ai_jobs_status_eligible
    ON aiprocessing.ai_jobs (status, next_eligible_at, queued_at);
