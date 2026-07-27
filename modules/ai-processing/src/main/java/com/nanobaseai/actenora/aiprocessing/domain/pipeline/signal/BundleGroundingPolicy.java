package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;

public interface BundleGroundingPolicy {
    ExtractionBundle retainGroundedItems(ExtractionBundle bundle, EvidenceIndex evidenceIndex);
}
