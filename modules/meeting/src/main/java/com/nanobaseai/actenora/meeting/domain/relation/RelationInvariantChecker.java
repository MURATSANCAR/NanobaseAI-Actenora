package com.nanobaseai.actenora.meeting.domain.relation;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces duplicate and cyclic relation invariants within a tenant.
 */
public final class RelationInvariantChecker {

    public void assertCanAdd(MeetingRelation candidate, List<MeetingRelation> existingInTenant) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(existingInTenant, "existingInTenant");

        for (MeetingRelation existing : existingInTenant) {
            if (!existing.tenantId().equals(candidate.tenantId())) {
                continue;
            }
            if (existing.matchesPair(
                    candidate.sourceOccurrenceId(),
                    candidate.targetOccurrenceId(),
                    candidate.relationType()
            )) {
                throw new DuplicateRelationException(candidate.relationType(),
                        candidate.sourceOccurrenceId(), candidate.targetOccurrenceId());
            }
        }

        if (candidate.relationType().participatesInFollowUpCycles()) {
            assertNoFollowUpCycle(candidate, existingInTenant);
        }
    }

    private void assertNoFollowUpCycle(MeetingRelation candidate, List<MeetingRelation> existingInTenant) {
        Map<UUID, Set<UUID>> adjacency = new HashMap<>();
        for (MeetingRelation relation : existingInTenant) {
            if (!relation.tenantId().equals(candidate.tenantId())) {
                continue;
            }
            if (relation.relationType() != RelationType.FOLLOW_UP) {
                continue;
            }
            adjacency
                    .computeIfAbsent(relation.sourceOccurrenceId(), ignored -> new HashSet<>())
                    .add(relation.targetOccurrenceId());
        }
        adjacency
                .computeIfAbsent(candidate.sourceOccurrenceId(), ignored -> new HashSet<>())
                .add(candidate.targetOccurrenceId());

        if (reaches(adjacency, candidate.targetOccurrenceId(), candidate.sourceOccurrenceId())) {
            throw new CyclicRelationException(
                    candidate.sourceOccurrenceId(),
                    candidate.targetOccurrenceId()
            );
        }
    }

    private static boolean reaches(Map<UUID, Set<UUID>> adjacency, UUID from, UUID to) {
        if (from.equals(to)) {
            return true;
        }
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new ArrayDeque<>();
        queue.add(from);
        while (!queue.isEmpty()) {
            UUID current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            for (UUID next : adjacency.getOrDefault(current, Set.of())) {
                if (next.equals(to)) {
                    return true;
                }
                queue.add(next);
            }
        }
        return false;
    }
}
