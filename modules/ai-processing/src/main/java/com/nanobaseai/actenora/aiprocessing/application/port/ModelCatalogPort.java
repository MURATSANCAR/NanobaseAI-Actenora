package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;

import java.util.List;

/**
 * Read port over the model/deployment registry for capability-based routing.
 */
public interface ModelCatalogPort {

    List<RoutableCandidate> findCandidates(AiCapability capability);
}
