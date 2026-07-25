package com.nanobaseai.actenora.transcript.domain;

/**
 * Lifecycle status for a transcript ingest record.
 */
public enum TranscriptStatus {
    UPLOADED,
    STORED,
    PENDING_PARSE,
    PARSED,
    PENDING_NORMALIZE,
    NORMALIZED,
    FAILED,
    DUPLICATE,
    DELETED
}
