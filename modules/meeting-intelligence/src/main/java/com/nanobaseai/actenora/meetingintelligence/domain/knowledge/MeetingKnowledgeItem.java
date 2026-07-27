package com.nanobaseai.actenora.meetingintelligence.domain.knowledge;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One approved structured note item indexed for hybrid retrieval.
 */
public record MeetingKnowledgeItem(
        UUID id,
        TenantId tenantId,
        UUID meetingOccurrenceId,
        UUID noteId,
        UUID noteVersionId,
        UUID sourceItemId,
        KnowledgeItemKind itemKind,
        String content,
        Optional<float[]> embedding,
        Instant createdAt
) {
    public MeetingKnowledgeItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(noteId, "noteId");
        Objects.requireNonNull(noteVersionId, "noteVersionId");
        Objects.requireNonNull(sourceItemId, "sourceItemId");
        Objects.requireNonNull(itemKind, "itemKind");
        Objects.requireNonNull(content, "content");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        embedding = embedding == null ? Optional.empty() : embedding.map(float[]::clone);
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static MeetingKnowledgeItem create(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId,
            UUID sourceItemId,
            KnowledgeItemKind itemKind,
            String content,
            float[] embedding,
            Instant createdAt
    ) {
        return new MeetingKnowledgeItem(
                UUID.randomUUID(),
                tenantId,
                meetingOccurrenceId,
                noteId,
                noteVersionId,
                sourceItemId,
                itemKind,
                content.trim(),
                Optional.ofNullable(embedding),
                createdAt
        );
    }
}
