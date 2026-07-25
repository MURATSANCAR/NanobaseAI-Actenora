package com.nanobaseai.actenora.meeting.application.collaboration;

import com.nanobaseai.actenora.meeting.application.collaboration.port.PrivateNoteRepository;
import com.nanobaseai.actenora.meeting.domain.collaboration.PrivateNote;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * FAZ 27 — private note retention deletion (body purged; no content logged).
 */
public final class PrivateNoteRetentionService {

    private final PrivateNoteRepository repository;

    public PrivateNoteRetentionService(PrivateNoteRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<PrivateNote> findExpired(TenantId tenantId, int retentionDays, Instant now) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(now, "now");
        Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
        return repository.findAllByTenantId(tenantId).stream()
                .filter(n -> !n.createdAt().isAfter(cutoff))
                .toList();
    }

    public void deleteForRetention(TenantId tenantId, UUID noteId) {
        repository.delete(noteId, tenantId);
    }
}
