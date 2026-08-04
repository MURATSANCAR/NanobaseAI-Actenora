package com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage;

/**
 * Pipeline stages that may emit item-level lineage events.
 * Observability only — does not alter extraction behavior.
 */
public enum LineageStage {
    LLM_RAW,
    JSON_REPAIR,
    SCHEMA_VALIDATION,
    MAPPING,
    GROUNDING,
    MERGE,
    PROPOSAL_RESOLUTION,
    SPEECH_ACT_CLASSIFICATION,
    MEETING_ITEM_POLICY,
    ACTION_POST_PROCESSING,
    CROSS_TYPE_RESOLUTION,
    DETERMINISTIC_VALIDATION,
    FINAL_NOTE_ASSEMBLY,
    FINAL_NOTE_MAPPING
}
