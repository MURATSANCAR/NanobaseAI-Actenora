package com.nanobaseai.actenora.operations.application.port;

import com.nanobaseai.actenora.operations.domain.retention.RetentionCandidate;

/** Performs physical/logical deletion for a retention candidate. */
public interface RetentionDeleter {

    void delete(RetentionCandidate candidate);
}
