-- Staged AI pipeline: extend ai_jobs as stage nodes + dependency DAG + artifacts.

ALTER TABLE aiprocessing.ai_jobs
    ADD COLUMN IF NOT EXISTS parent_job_id UUID REFERENCES aiprocessing.ai_jobs (id),
    ADD COLUMN IF NOT EXISTS stage VARCHAR(40),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(256),
    ADD COLUMN IF NOT EXISTS chunk_index INTEGER,
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS error_message TEXT;

-- Backfill stage + idempotency for existing rows (legacy monolith path).
UPDATE aiprocessing.ai_jobs
SET stage = COALESCE(stage, CASE
        WHEN task_type = 'CHUNK_EXTRACTION' THEN 'LEGACY'
        WHEN task_type = 'FINAL_NOTE' THEN 'MINUTES'
        WHEN task_type = 'CANDIDATE_MERGE' THEN 'MERGE'
        WHEN task_type = 'VALIDATION' THEN 'VALIDATE'
        WHEN task_type = 'MEETING_TRIAGE' THEN 'TRIAGE'
        WHEN task_type = 'EMBEDDING' THEN 'EMBEDDING'
        ELSE 'LEGACY'
    END),
    idempotency_key = COALESCE(
        idempotency_key,
        'legacy:' || tenant_id::text || ':' || id::text
    )
WHERE stage IS NULL OR idempotency_key IS NULL;

ALTER TABLE aiprocessing.ai_jobs
    ALTER COLUMN stage SET NOT NULL,
    ALTER COLUMN stage SET DEFAULT 'LEGACY',
    ALTER COLUMN idempotency_key SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_jobs_tenant_idempotency
    ON aiprocessing.ai_jobs (tenant_id, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_ai_jobs_parent
    ON aiprocessing.ai_jobs (parent_job_id);

CREATE INDEX IF NOT EXISTS idx_ai_jobs_stage_status
    ON aiprocessing.ai_jobs (stage, status, queued_at);

CREATE INDEX IF NOT EXISTS idx_ai_jobs_pending_eligible
    ON aiprocessing.ai_jobs (priority DESC, queued_at)
    WHERE status = 'QUEUED';

CREATE TABLE IF NOT EXISTS aiprocessing.processing_job_dependency (
    job_id              UUID NOT NULL REFERENCES aiprocessing.ai_jobs (id) ON DELETE CASCADE,
    depends_on_job_id   UUID NOT NULL REFERENCES aiprocessing.ai_jobs (id) ON DELETE CASCADE,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (job_id, depends_on_job_id),
    CONSTRAINT chk_processing_dep_not_self CHECK (job_id <> depends_on_job_id),
    CONSTRAINT chk_processing_dep_status CHECK (status IN ('PENDING', 'SATISFIED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_processing_dep_depends_on
    ON aiprocessing.processing_job_dependency (depends_on_job_id);

CREATE INDEX IF NOT EXISTS idx_processing_dep_job_status
    ON aiprocessing.processing_job_dependency (job_id, status);

CREATE TABLE IF NOT EXISTS aiprocessing.processing_artifact (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    job_id                  UUID NOT NULL REFERENCES aiprocessing.ai_jobs (id) ON DELETE CASCADE,
    meeting_occurrence_id   UUID NOT NULL,
    artifact_type           VARCHAR(64) NOT NULL,
    object_key              TEXT,
    content_hash            VARCHAR(128),
    content_type            VARCHAR(128),
    size_bytes              BIGINT,
    payload_json            JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_processing_artifact_job
    ON aiprocessing.processing_artifact (job_id);

CREATE INDEX IF NOT EXISTS idx_processing_artifact_meeting
    ON aiprocessing.processing_artifact (tenant_id, meeting_occurrence_id, artifact_type);
