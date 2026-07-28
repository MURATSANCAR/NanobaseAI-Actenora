package com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact;

/**
 * PR-1 stub: never classifies; hybrid falls through to UNKNOWN.
 */
public final class NoOpSemanticSpeechActClassifier implements SemanticSpeechActClassifier {
    @Override
    public SpeechActResult classify(String text) {
        return SpeechActResult.unknown();
    }
}
