package com.nanobaseai.actenora.modelmanagement.application;

import com.nanobaseai.actenora.modelmanagement.domain.ModelCapabilityType;

public record ConfigureCapabilityCommand(
        ModelCapabilityType capability,
        double qualityScore,
        double speedScore,
        int minContextRequired,
        boolean enabled
) {
}
