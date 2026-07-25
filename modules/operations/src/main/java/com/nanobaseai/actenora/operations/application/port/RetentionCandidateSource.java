package com.nanobaseai.actenora.operations.application.port;

import com.nanobaseai.actenora.operations.domain.retention.RetentionCandidate;

import java.time.Instant;
import java.util.List;

/** Supplies expired retention candidates from owning bounded contexts. */
public interface RetentionCandidateSource {

    List<RetentionCandidate> findExpired(Instant now);
}
