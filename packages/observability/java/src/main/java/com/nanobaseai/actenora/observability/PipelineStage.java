package com.nanobaseai.actenora.observability;

/**
 * End-to-end meeting pipeline stages for a single distributed trace (FAZ 25).
 *
 * <pre>
 * MeetingDiscovered → MeetingEnded → TranscriptFetched → TranscriptNormalized
 * → AiJobRouted → InferenceCompleted → ValidationCompleted → NoteDrafted
 * → Approved → Rendered → Delivered
 * </pre>
 */
public enum PipelineStage {
    MEETING_DISCOVERED("MeetingDiscovered"),
    MEETING_ENDED("MeetingEnded"),
    TRANSCRIPT_FETCHED("TranscriptFetched"),
    TRANSCRIPT_NORMALIZED("TranscriptNormalized"),
    AI_JOB_ROUTED("AiJobRouted"),
    INFERENCE_COMPLETED("InferenceCompleted"),
    VALIDATION_COMPLETED("ValidationCompleted"),
    NOTE_DRAFTED("NoteDrafted"),
    APPROVED("Approved"),
    RENDERED("Rendered"),
    DELIVERED("Delivered");

    private final String spanName;

    PipelineStage(String spanName) {
        this.spanName = spanName;
    }

    public String spanName() {
        return spanName;
    }

    public int order() {
        return ordinal();
    }

    public boolean isBefore(PipelineStage other) {
        return ordinal() < other.ordinal();
    }

    public PipelineStage next() {
        int next = ordinal() + 1;
        if (next >= values().length) {
            throw new IllegalStateException("No stage after " + this);
        }
        return values()[next];
    }

    public static PipelineStage first() {
        return MEETING_DISCOVERED;
    }

    public static PipelineStage last() {
        return DELIVERED;
    }
}
