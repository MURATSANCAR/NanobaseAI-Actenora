package com.nanobaseai.actenora.operations.application;

import com.nanobaseai.actenora.operations.application.port.LegalHoldRepository;
import com.nanobaseai.actenora.operations.application.port.RetentionAuditSink;
import com.nanobaseai.actenora.operations.application.port.RetentionCandidateSource;
import com.nanobaseai.actenora.operations.application.port.RetentionDeleter;
import com.nanobaseai.actenora.operations.domain.retention.LegalHold;
import com.nanobaseai.actenora.operations.domain.retention.LegalHoldBlockedException;
import com.nanobaseai.actenora.operations.domain.retention.RetentionCandidate;
import com.nanobaseai.actenora.operations.domain.retention.RetentionGuard;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * FAZ 27 retention job — deletes expired transcripts / private notes / objects unless legal-held.
 */
public final class RetentionJobService {

    private final RetentionCandidateSource candidateSource;
    private final LegalHoldRepository legalHoldRepository;
    private final RetentionDeleter deleter;
    private final RetentionAuditSink auditSink;
    private final InstantClock clock;

    public RetentionJobService(
            RetentionCandidateSource candidateSource,
            LegalHoldRepository legalHoldRepository,
            RetentionDeleter deleter,
            RetentionAuditSink auditSink,
            InstantClock clock
    ) {
        this.candidateSource = Objects.requireNonNull(candidateSource, "candidateSource");
        this.legalHoldRepository = Objects.requireNonNull(legalHoldRepository, "legalHoldRepository");
        this.deleter = Objects.requireNonNull(deleter, "deleter");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RetentionRunResult runOnce() {
        String correlationId = UUID.randomUUID().toString();
        var now = clock.now();
        List<RetentionCandidate> expired = candidateSource.findExpired(now);
        int deleted = 0;
        int blocked = 0;
        int failed = 0;
        List<String> blockedIds = new ArrayList<>();

        for (RetentionCandidate candidate : expired) {
            List<LegalHold> holds = legalHoldRepository.findActiveForResource(
                    candidate.tenantId(),
                    candidate.resourceType(),
                    candidate.resourceId());
            try {
                RetentionGuard.assertDeletable(candidate, holds);
                deleter.delete(candidate);
                auditSink.recordDeletion(candidate, correlationId);
                deleted++;
            } catch (LegalHoldBlockedException ex) {
                auditSink.recordLegalHoldBlocked(candidate, correlationId);
                blocked++;
                blockedIds.add(candidate.resourceType() + ":" + candidate.resourceId());
            } catch (RuntimeException ex) {
                failed++;
            }
        }
        return new RetentionRunResult(expired.size(), deleted, blocked, failed, blockedIds, correlationId);
    }

    public record RetentionRunResult(
            int scanned,
            int deleted,
            int blockedByLegalHold,
            int failed,
            List<String> blockedResourceIds,
            String correlationId
    ) {
    }
}
