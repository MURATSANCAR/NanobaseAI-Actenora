package com.nanobaseai.actenora.aiprocessing.domain.job;

/**
 * Pipeline stage for staged AI processing (maps to RabbitMQ stage queues).
 */
public enum ProcessingStage {
    ROOT,
    NORMALIZE,
    TRIAGE,
    CHUNK,
    EXTRACT,
    MERGE,
    VALIDATE,
    MINUTES,
    EMBEDDING,
    /** Monolithic in-process pipeline (pre-staged / kill-switch). */
    LEGACY;

    public String queueSuffix() {
        return switch (this) {
            case ROOT -> "normalize"; // root only admits; first work is normalize
            case NORMALIZE -> "normalize";
            case TRIAGE -> "triage";
            case CHUNK -> "chunk";
            case EXTRACT -> "extract";
            case MERGE -> "merge";
            case VALIDATE -> "validate";
            case MINUTES -> "minutes";
            case EMBEDDING -> "embed";
            case LEGACY -> "extract"; // legacy still claimed via worker poll
        };
    }

    public static ProcessingStage fromTaskType(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return LEGACY;
        }
        return switch (taskType.trim().toUpperCase()) {
            case "PIPELINE_ROOT" -> ROOT;
            case "NORMALIZE", "TRANSCRIPT_NORMALIZE" -> NORMALIZE;
            case "MEETING_TRIAGE" -> TRIAGE;
            case "CHUNK_PLAN", "CHUNK" -> CHUNK;
            // Monolith admit uses task_type CHUNK_EXTRACTION without explicit stage;
            // staged extract nodes set ProcessingStage.EXTRACT via enqueueStaged.
            case "CHUNK_EXTRACTION" -> LEGACY;
            case "CANDIDATE_MERGE" -> MERGE;
            case "VALIDATION", "DETERMINISTIC_VALIDATE" -> VALIDATE;
            case "FINAL_NOTE" -> MINUTES;
            case "EMBEDDING" -> EMBEDDING;
            default -> LEGACY;
        };
    }
}
