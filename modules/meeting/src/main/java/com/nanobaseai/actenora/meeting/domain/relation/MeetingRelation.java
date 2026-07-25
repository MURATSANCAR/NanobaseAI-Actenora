package com.nanobaseai.actenora.meeting.domain.relation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Confirmed relationship between two meeting occurrences.
 * AI suggestions never materialize this aggregate directly.
 */
public final class MeetingRelation {

    private final UUID id;
    private final UUID tenantId;
    private final UUID sourceOccurrenceId;
    private final UUID targetOccurrenceId;
    private final RelationType relationType;
    private final String createdBy;
    private final UUID suggestionId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    private MeetingRelation(
            UUID id,
            UUID tenantId,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            RelationType relationType,
            String createdBy,
            UUID suggestionId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.sourceOccurrenceId = Objects.requireNonNull(sourceOccurrenceId, "sourceOccurrenceId");
        this.targetOccurrenceId = Objects.requireNonNull(targetOccurrenceId, "targetOccurrenceId");
        this.relationType = Objects.requireNonNull(relationType, "relationType");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.suggestionId = suggestionId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
        if (sourceOccurrenceId.equals(targetOccurrenceId)) {
            throw new IllegalArgumentException("source and target occurrence must differ");
        }
    }

    public static MeetingRelation createManual(
            UUID tenantId,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            RelationType relationType,
            String createdBy,
            Instant now
    ) {
        if (relationType == RelationType.AI_SUGGESTED) {
            throw new IllegalArgumentException("AI_SUGGESTED relations must originate from an approved suggestion");
        }
        return new MeetingRelation(
                UUID.randomUUID(),
                tenantId,
                sourceOccurrenceId,
                targetOccurrenceId,
                relationType,
                createdBy,
                null,
                now,
                now,
                0L
        );
    }

    public static MeetingRelation fromApprovedSuggestion(
            MeetingRelationSuggestion suggestion,
            Instant now
    ) {
        Objects.requireNonNull(suggestion, "suggestion");
        if (suggestion.status() != SuggestionStatus.APPROVED) {
            throw new IllegalStateException("suggestion must be approved before creating a relation");
        }
        return new MeetingRelation(
                UUID.randomUUID(),
                suggestion.tenantId(),
                suggestion.sourceOccurrenceId(),
                suggestion.targetOccurrenceId(),
                RelationType.AI_SUGGESTED,
                "suggestion:" + suggestion.id(),
                suggestion.id(),
                now,
                now,
                0L
        );
    }

    public static MeetingRelation rehydrate(
            UUID id,
            UUID tenantId,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            RelationType relationType,
            String createdBy,
            UUID suggestionId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new MeetingRelation(
                id,
                tenantId,
                sourceOccurrenceId,
                targetOccurrenceId,
                relationType,
                createdBy,
                suggestionId,
                createdAt,
                updatedAt,
                version
        );
    }

    public boolean matchesPair(UUID sourceId, UUID targetId, RelationType type) {
        if (relationType != type) {
            return false;
        }
        if (type.isDirected()) {
            return sourceOccurrenceId.equals(sourceId) && targetOccurrenceId.equals(targetId);
        }
        return (sourceOccurrenceId.equals(sourceId) && targetOccurrenceId.equals(targetId))
                || (sourceOccurrenceId.equals(targetId) && targetOccurrenceId.equals(sourceId));
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID sourceOccurrenceId() {
        return sourceOccurrenceId;
    }

    public UUID targetOccurrenceId() {
        return targetOccurrenceId;
    }

    public RelationType relationType() {
        return relationType;
    }

    public String createdBy() {
        return createdBy;
    }

    public UUID suggestionId() {
        return suggestionId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}
