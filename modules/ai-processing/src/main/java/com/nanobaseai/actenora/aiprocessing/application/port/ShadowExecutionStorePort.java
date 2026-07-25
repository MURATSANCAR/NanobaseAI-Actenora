package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.routing.ShadowExecution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShadowExecutionStorePort {

    void save(ShadowExecution shadowExecution);

    Optional<ShadowExecution> findById(UUID shadowId);

    List<ShadowExecution> findByJobId(UUID jobId);
}
