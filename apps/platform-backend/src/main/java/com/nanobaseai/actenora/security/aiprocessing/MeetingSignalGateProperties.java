package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.SignalGateConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code actenora.meeting.signal-gate.*} and {@code actenora.meeting.speech-signals.*}.
 */
@ConfigurationProperties(prefix = "actenora.meeting")
public class MeetingSignalGateProperties {

    private final SignalGate signalGate = new SignalGate();
    private final SpeechSignals speechSignals = new SpeechSignals();

    public SignalGate getSignalGate() {
        return signalGate;
    }

    public SpeechSignals getSpeechSignals() {
        return speechSignals;
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
}
