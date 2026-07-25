package com.nanobaseai.actenora.modelmanagement.domain;

import java.util.Objects;

/**
 * Capability configuration bound to a model definition.
 */
public final class ModelCapability {

    private final ModelCapabilityType capability;
    private double qualityScore;
    private double speedScore;
    private int minContextRequired;
    private boolean enabled;

    public ModelCapability(
            ModelCapabilityType capability,
            double qualityScore,
            double speedScore,
            int minContextRequired,
            boolean enabled
    ) {
        this.capability = Objects.requireNonNull(capability, "capability");
        if (minContextRequired < 0) {
            throw ModelRegistryException.invalidContextSize("min_context_required must be >= 0");
        }
        this.qualityScore = qualityScore;
        this.speedScore = speedScore;
        this.minContextRequired = minContextRequired;
        this.enabled = enabled;
    }

    public ModelCapabilityType capability() {
        return capability;
    }

    public double qualityScore() {
        return qualityScore;
    }

    public double speedScore() {
        return speedScore;
    }

    public int minContextRequired() {
        return minContextRequired;
    }

    public boolean enabled() {
        return enabled;
    }

    public void reconfigure(double qualityScore, double speedScore, int minContextRequired, boolean enabled) {
        if (minContextRequired < 0) {
            throw ModelRegistryException.invalidContextSize("min_context_required must be >= 0");
        }
        this.qualityScore = qualityScore;
        this.speedScore = speedScore;
        this.minContextRequired = minContextRequired;
        this.enabled = enabled;
    }

    public void assertFitsModelContext(int modelContextWindow) {
        if (minContextRequired > modelContextWindow) {
            throw ModelRegistryException.invalidContextSize(
                    "min_context_required (" + minContextRequired
                            + ") exceeds model context_window (" + modelContextWindow + ")"
            );
        }
    }
}
