package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelChangeProvenance;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingDecision;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoutingDecisionStorePort {

    void save(RoutingDecision decision);

    void saveProvenance(ModelChangeProvenance provenance);

    Optional<RoutingDecision> findById(UUID decisionId);

    List<RoutingDecision> findByJobId(UUID jobId);

    List<ModelChangeProvenance> findProvenanceByJobId(UUID jobId);
}
