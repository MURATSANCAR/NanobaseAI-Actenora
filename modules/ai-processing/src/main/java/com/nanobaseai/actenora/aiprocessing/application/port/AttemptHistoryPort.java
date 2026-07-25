package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.routing.AttemptHistory;
import com.nanobaseai.actenora.aiprocessing.domain.routing.AttemptRecord;

import java.util.Optional;
import java.util.UUID;

public interface AttemptHistoryPort {

    AttemptHistory getOrCreate(UUID jobId);

    void append(AttemptRecord attempt);

    void complete(AttemptRecord completed);

    Optional<AttemptHistory> find(UUID jobId);
}
