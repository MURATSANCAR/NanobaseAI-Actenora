package com.nanobaseai.actenora.transcript.infrastructure;

import com.nanobaseai.actenora.transcript.domain.TranscriptEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Transcript persistence. Forbidden for other modules to inject.
 */
public interface TranscriptRepository {

    Optional<TranscriptEntity> findById(UUID id);

    TranscriptEntity save(TranscriptEntity transcript);
}
