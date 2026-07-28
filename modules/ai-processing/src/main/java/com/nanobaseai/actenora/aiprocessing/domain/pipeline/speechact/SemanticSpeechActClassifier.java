package com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact;

/**
 * Optional semantic speech-act classifier. PR-1 ships a NoOp implementation.
 */
public interface SemanticSpeechActClassifier {
    SpeechActResult classify(String text);
}
