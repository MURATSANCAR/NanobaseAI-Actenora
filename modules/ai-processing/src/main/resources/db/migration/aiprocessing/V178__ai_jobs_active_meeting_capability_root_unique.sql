-- Multi-replica race hardening: at most one active *root* job per meeting+capability.
-- Child stage nodes (parent_job_id IS NOT NULL) are excluded so staged DAG chunks remain valid.
-- Application admission already checks meeting+capability; this closes the TOCTOU window.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_jobs_active_meeting_capability_root
    ON aiprocessing.ai_jobs (tenant_id, meeting_occurrence_id, requested_capability)
    WHERE status IN ('QUEUED', 'RUNNING')
      AND parent_job_id IS NULL;
