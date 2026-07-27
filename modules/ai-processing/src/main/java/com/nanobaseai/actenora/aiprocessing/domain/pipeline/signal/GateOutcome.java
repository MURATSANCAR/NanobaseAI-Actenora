package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

public enum GateOutcome {
    EXTRACT_STRONG_SIGNAL,
    EXTRACT_COMPOSITE_SIGNAL,
    EXTRACT_CONTINUATION,
    SKIP_LOW_SIGNAL,
    SHADOW_SKIP
}
