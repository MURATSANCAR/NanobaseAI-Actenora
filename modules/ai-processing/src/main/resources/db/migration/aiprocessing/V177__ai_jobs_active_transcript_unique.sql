-- Harden legacy admit: at most one active job per transcript+taskType.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_jobs_active_transcript_task
    ON aiprocessing.ai_jobs (tenant_id, transcript_id, task_type)
    WHERE status IN ('QUEUED', 'RUNNING');
