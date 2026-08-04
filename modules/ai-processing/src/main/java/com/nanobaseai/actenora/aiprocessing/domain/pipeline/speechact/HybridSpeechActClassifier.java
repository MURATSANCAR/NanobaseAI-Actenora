package com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingQualityProperties;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.ItemLineageRecord;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageOperation;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageReasonCode;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageStage;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageSupport;

import java.util.List;
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
        SpeechActResult result;
        if (det.speechAct() != MeetingSpeechAct.UNKNOWN
                && det.confidence() >= quality.deterministicApplyMinConfidence()) {
            result = det;
        } else {
            SpeechActResult sem = semantic.classify(text);
            if (sem.speechAct() == MeetingSpeechAct.UNKNOWN) {
                result = det.speechAct() == MeetingSpeechAct.UNKNOWN ? SpeechActResult.unknown() : det;
            } else if (sem.confidence() >= quality.semanticApplyMinConfidence()) {
                result = new SpeechActResult(
                        sem.speechAct(),
                        sem.confidence(),
                        ClassificationSource.HYBRID,
                        sem.reasonCode()
                );
            } else {
                result = SpeechActResult.unknown();
            }
        }
        observe(text, result);
        return result;
    }

    private static void observe(String text, SpeechActResult result) {
        LineageReasonCode reason = switch (result.speechAct()) {
            case STATUS_QUO -> LineageReasonCode.SPEECH_ACT_STATUS_QUO;
            case EXPLICIT_DECISION -> LineageReasonCode.SPEECH_ACT_EXPLICIT_DECISION;
            case PROPOSAL_CUE -> LineageReasonCode.SPEECH_ACT_PROPOSAL;
            default -> LineageReasonCode.SPEECH_ACT_UNKNOWN;
        };
        LineageSupport.record(
                LineageSupport.idOf("speech", text, List.of()),
                "SPEECH_ACT",
                LineageStage.SPEECH_ACT_CLASSIFICATION,
                LineageOperation.FLAG,
                reason,
                List.of(),
                null,
                ItemLineageRecord.snapshot(text, null, null, List.of()),
                ItemLineageRecord.snapshot(text, null, null, List.of()),
                "hybrid-speech-act-v1",
                null,
                null,
                null
        );
    }

    public DeterministicSpeechActMatcher deterministic() {
        return deterministic;
    }
}
