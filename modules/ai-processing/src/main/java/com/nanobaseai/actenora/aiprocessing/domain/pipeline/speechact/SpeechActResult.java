package com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact;

import java.util.Objects;

public record SpeechActResult(
        MeetingSpeechAct speechAct,
        double confidence,
        ClassificationSource source,
        String reasonCode
) {
    public SpeechActResult {
        Objects.requireNonNull(speechAct, "speechAct");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reasonCode, "reasonCode");
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be in [0,1]");
        }
    }

    public static SpeechActResult unknown() {
        return new SpeechActResult(MeetingSpeechAct.UNKNOWN, 0.0d, ClassificationSource.UNKNOWN, "none");
    }
}
