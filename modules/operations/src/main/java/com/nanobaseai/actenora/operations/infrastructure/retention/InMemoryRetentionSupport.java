package com.nanobaseai.actenora.operations.infrastructure.retention;

import com.nanobaseai.actenora.operations.application.port.RetentionAuditSink;
import com.nanobaseai.actenora.operations.application.port.RetentionCandidateSource;
import com.nanobaseai.actenora.operations.application.port.RetentionDeleter;
import com.nanobaseai.actenora.operations.domain.retention.RetentionCandidate;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory retention adapters for unit tests and local dry-runs. */
public final class InMemoryRetentionSupport
        implements RetentionCandidateSource, RetentionDeleter, RetentionAuditSink {

    private final List<RetentionCandidate> candidates = new CopyOnWriteArrayList<>();
    private final List<String> deletedIds = new CopyOnWriteArrayList<>();
    private final List<String> auditEvents = new CopyOnWriteArrayList<>();
    private final Map<String, RetentionCandidate> byId = new ConcurrentHashMap<>();

    public void addCandidate(RetentionCandidate candidate) {
        candidates.add(candidate);
        byId.put(key(candidate), candidate);
    }

    @Override
    public List<RetentionCandidate> findExpired(Instant now) {
        return candidates.stream().filter(c -> c.isExpired(now)).toList();
    }

    @Override
    public void delete(RetentionCandidate candidate) {
        deletedIds.add(key(candidate));
        candidates.removeIf(c -> key(c).equals(key(candidate)));
        byId.remove(key(candidate));
    }

    @Override
    public void recordDeletion(RetentionCandidate candidate, String correlationId) {
        auditEvents.add("DELETED|" + key(candidate) + "|" + correlationId);
    }

    @Override
    public void recordLegalHoldBlocked(RetentionCandidate candidate, String correlationId) {
        auditEvents.add("BLOCKED|" + key(candidate) + "|" + correlationId);
    }

    @Override
    public void recordLegalHoldPlaced(
            TenantId tenantId,
            String resourceType,
            String resourceId,
            String reason,
            String correlationId
    ) {
        auditEvents.add("HOLD|" + tenantId.value() + "|" + resourceType + "|" + resourceId + "|" + correlationId);
    }

    public List<String> deletedIds() {
        return List.copyOf(deletedIds);
    }

    public List<String> auditEvents() {
        return List.copyOf(auditEvents);
    }

    public boolean stillPresent(RetentionCandidate candidate) {
        return byId.containsKey(key(candidate));
    }

    public void clear() {
        candidates.clear();
        deletedIds.clear();
        auditEvents.clear();
        byId.clear();
    }

    private static String key(RetentionCandidate candidate) {
        return candidate.resourceType() + ":" + candidate.resourceId();
    }
}
