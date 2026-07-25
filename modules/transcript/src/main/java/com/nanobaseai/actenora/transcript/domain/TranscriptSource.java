package com.nanobaseai.actenora.transcript.domain;

/**
 * Provenance of transcript bytes. Manual VTT upload is independent of Teams Graph.
 */
public enum TranscriptSource {
    MANUAL_UPLOAD,
    TEAMS_GRAPH,
    EXTERNAL_IMPORT
}
