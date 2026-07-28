package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingQualityProperties;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.SignalGateConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code actenora.meeting.signal-gate.*}, {@code actenora.meeting.speech-signals.*},
 * and {@code actenora.meeting.quality.*}.
 */
@ConfigurationProperties(prefix = "actenora.meeting")
public class MeetingSignalGateProperties {

    private final SignalGate signalGate = new SignalGate();
    private final SpeechSignals speechSignals = new SpeechSignals();
    private final Quality quality = new Quality();

    public SignalGate getSignalGate() {
        return signalGate;
    }

    public SpeechSignals getSpeechSignals() {
        return speechSignals;
    }

    public Quality getQuality() {
        return quality;
    }

    public SignalGateConfig toConfig() {
        SignalGateConfig defaults = SignalGateConfig.productionDefaults();
        return new SignalGateConfig(
                signalGate.isEnabled(),
                blankTo(signalGate.getMode(), defaults.mode()),
                signalGate.getThreshold() > 0 ? signalGate.getThreshold() : defaults.threshold(),
                signalGate.isContinuationAware(),
                signalGate.isSemanticRepetitionEnabled(),
                signalGate.isHardMarkerShortcutEnabled(),
                signalGate.isShadowMode(),
                signalGate.isClassifierEnabled(),
                signalGate.getUncertainBand() >= 0 ? signalGate.getUncertainBand() : defaults.uncertainBand(),
                blankTo(signalGate.getPolicyVersion(), defaults.policyVersion()),
                blankTo(speechSignals.getDictionaryVersion(), defaults.dictionaryVersion())
        );
    }

    public MeetingQualityProperties toMeetingQualityProperties() {
        MeetingQualityProperties defaults = MeetingQualityProperties.defaults();
        return new MeetingQualityProperties(
                quality.getSynthesisFallbackConfidenceCap() > 0
                        ? quality.getSynthesisFallbackConfidenceCap()
                        : defaults.synthesisFallbackConfidenceCap(),
                quality.getAuditFallbackConfidenceCap() > 0
                        ? quality.getAuditFallbackConfidenceCap()
                        : defaults.auditFallbackConfidenceCap(),
                quality.getDoubleFallbackConfidenceCap() > 0
                        ? quality.getDoubleFallbackConfidenceCap()
                        : defaults.doubleFallbackConfidenceCap(),
                quality.isManualReviewOnAnyFallback(),
                quality.getSemanticApplyMinConfidence() > 0
                        ? quality.getSemanticApplyMinConfidence()
                        : defaults.semanticApplyMinConfidence(),
                quality.getSemanticReviewMinConfidence() > 0
                        ? quality.getSemanticReviewMinConfidence()
                        : defaults.semanticReviewMinConfidence(),
                quality.getDeterministicApplyMinConfidence() > 0
                        ? quality.getDeterministicApplyMinConfidence()
                        : defaults.deterministicApplyMinConfidence()
        );
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public static class SignalGate {
        private boolean enabled = true;
        private String mode = "adaptive";
        private double threshold = 4.5d;
        private boolean continuationAware = true;
        private boolean semanticRepetitionEnabled = true;
        private boolean hardMarkerShortcutEnabled = true;
        private boolean shadowMode = false;
        private boolean classifierEnabled = true;
        private double uncertainBand = 2.0d;
        private String policyVersion = "adaptive-gate-v3";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
        public boolean isContinuationAware() { return continuationAware; }
        public void setContinuationAware(boolean continuationAware) { this.continuationAware = continuationAware; }
        public boolean isSemanticRepetitionEnabled() { return semanticRepetitionEnabled; }
        public void setSemanticRepetitionEnabled(boolean semanticRepetitionEnabled) {
            this.semanticRepetitionEnabled = semanticRepetitionEnabled;
        }
        public boolean isHardMarkerShortcutEnabled() { return hardMarkerShortcutEnabled; }
        public void setHardMarkerShortcutEnabled(boolean hardMarkerShortcutEnabled) {
            this.hardMarkerShortcutEnabled = hardMarkerShortcutEnabled;
        }
        public boolean isShadowMode() { return shadowMode; }
        public void setShadowMode(boolean shadowMode) { this.shadowMode = shadowMode; }
        public boolean isClassifierEnabled() { return classifierEnabled; }
        public void setClassifierEnabled(boolean classifierEnabled) { this.classifierEnabled = classifierEnabled; }
        public double getUncertainBand() { return uncertainBand; }
        public void setUncertainBand(double uncertainBand) { this.uncertainBand = uncertainBand; }
        public String getPolicyVersion() { return policyVersion; }
        public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    }

    public static class SpeechSignals {
        private String dictionaryVersion = "tr-en-v1";

        public String getDictionaryVersion() { return dictionaryVersion; }
        public void setDictionaryVersion(String dictionaryVersion) { this.dictionaryVersion = dictionaryVersion; }
    }

    public static class Quality {
        private double synthesisFallbackConfidenceCap = 0.65d;
        private double auditFallbackConfidenceCap = 0.55d;
        private double doubleFallbackConfidenceCap = 0.45d;
        private boolean manualReviewOnAnyFallback = true;
        private double semanticApplyMinConfidence = 0.90d;
        private double semanticReviewMinConfidence = 0.65d;
        private double deterministicApplyMinConfidence = 0.90d;

        public double getSynthesisFallbackConfidenceCap() { return synthesisFallbackConfidenceCap; }
        public void setSynthesisFallbackConfidenceCap(double synthesisFallbackConfidenceCap) {
            this.synthesisFallbackConfidenceCap = synthesisFallbackConfidenceCap;
        }
        public double getAuditFallbackConfidenceCap() { return auditFallbackConfidenceCap; }
        public void setAuditFallbackConfidenceCap(double auditFallbackConfidenceCap) {
            this.auditFallbackConfidenceCap = auditFallbackConfidenceCap;
        }
        public double getDoubleFallbackConfidenceCap() { return doubleFallbackConfidenceCap; }
        public void setDoubleFallbackConfidenceCap(double doubleFallbackConfidenceCap) {
            this.doubleFallbackConfidenceCap = doubleFallbackConfidenceCap;
        }
        public boolean isManualReviewOnAnyFallback() { return manualReviewOnAnyFallback; }
        public void setManualReviewOnAnyFallback(boolean manualReviewOnAnyFallback) {
            this.manualReviewOnAnyFallback = manualReviewOnAnyFallback;
        }
        public double getSemanticApplyMinConfidence() { return semanticApplyMinConfidence; }
        public void setSemanticApplyMinConfidence(double semanticApplyMinConfidence) {
            this.semanticApplyMinConfidence = semanticApplyMinConfidence;
        }
        public double getSemanticReviewMinConfidence() { return semanticReviewMinConfidence; }
        public void setSemanticReviewMinConfidence(double semanticReviewMinConfidence) {
            this.semanticReviewMinConfidence = semanticReviewMinConfidence;
        }
        public double getDeterministicApplyMinConfidence() { return deterministicApplyMinConfidence; }
        public void setDeterministicApplyMinConfidence(double deterministicApplyMinConfidence) {
            this.deterministicApplyMinConfidence = deterministicApplyMinConfidence;
        }
    }
}
