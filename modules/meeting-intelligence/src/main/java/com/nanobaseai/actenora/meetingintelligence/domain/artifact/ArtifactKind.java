package com.nanobaseai.actenora.meetingintelligence.domain.artifact;

/**
 * Logical kinds for MinIO/S3 object keys registered in artifact_metadata.
 */
public enum ArtifactKind {
    TRANSCRIPT_RAW,
    TRANSCRIPT_NORMALIZED,
    NOTE_DRAFT,
    NOTE_APPROVED,
    EXTRACTION_BUNDLE,
    OTHER
}
