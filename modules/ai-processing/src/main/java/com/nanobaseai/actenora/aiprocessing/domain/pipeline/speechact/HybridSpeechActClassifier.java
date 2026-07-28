package com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingQualityProperties;

import java.util.Objects;

/**
 * Deterministic-first hybrid classifier. Semantic path is NoOp in PR-1.
 */
public final class HybridSpeechActClassifier {

    private final DeterministicSpeechActMatcher deterministic;
    private final SemanticSpeechActClassifier semantic;
    private final MeetingQualityProperties quality;

    public HybridSpeechActClassifier(
            DeterministicSpeechActMatcher deterministic,
            SemanticSpeechActClassifier semantic,
            MeetingQualityProperties quality
    ) {
        this.deterministic = Objects.requireNonNull(deterministic, "deterministic");
        this.semantic = Objects.requireNonNull(semantic, "semantic");
        this.quality = Objects.requireNonNull(quality, "quality");
    }

    public static HybridSpeechActClassifier productionDefaults() {
        return new HybridSpeechActClassifier(
                DeterministicSpeechActMatcher.loadDefaultTr(),
                new NoOpSemanticSpeechActClassifier(),
                MeetingQualityProperties.load()
        );
    }

    public SpeechActResult classify(String text) {
        SpeechActResult det = deterministic.classify(text);
        if (det.speechAct() != MeetingSpeechAct.UNKNOWN
                && det.confidence() >= quality.deterministicApplyMinConfidence()) {
            return det;
        }
        SpeechActResult sem = semantic.classify(text);
        if (sem.speechAct() == MeetingSpeechAct.UNKNOWN) {
            return det.speechAct() == MeetingSpeechAct.UNKNOWN ? SpeechActResult.unknown() : det;
        }
        if (sem.confidence() >= quality.semanticApplyMinConfidence()) {
            return new SpeechActResult(
                    sem.speechAct(),
                    sem.confidence(),
                    ClassificationSource.HYBRID,
                    sem.reasonCode()
            );
        }
        return SpeechActResult.unknown();
    }

    public DeterministicSpeechActMatcher deterministic() {
        return deterministic;
    }
}
