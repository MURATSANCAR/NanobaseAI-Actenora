package com.nanobaseai.actenora.transcript.domain.normalization;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Speaker candidate resolution for one original segment label.
 */
public final class SpeakerResolution {

    private final UUID id;
    private final UUID originalSegmentId;
    private final String rawDisplayName;
    private final SpeakerResolutionStatus status;
    private final UUID resolvedEntryId;
    private final String resolvedCanonical;
    private final List<UUID> candidateEntryIds;

    public SpeakerResolution(
            UUID id,
            UUID originalSegmentId,
            String rawDisplayName,
            SpeakerResolutionStatus status,
            UUID resolvedEntryId,
            String resolvedCanonical,
            List<UUID> candidateEntryIds) {
        this.id = Objects.requireNonNull(id, "id");
        this.originalSegmentId = Objects.requireNonNull(originalSegmentId, "originalSegmentId");
        this.rawDisplayName = rawDisplayName;
        this.status = Objects.requireNonNull(status, "status");
        this.resolvedEntryId = resolvedEntryId;
        this.resolvedCanonical = resolvedCanonical;
        this.candidateEntryIds = List.copyOf(candidateEntryIds == null ? List.of() : candidateEntryIds);
        if ((status == SpeakerResolutionStatus.RESOLVED_EXACT
                || status == SpeakerResolutionStatus.RESOLVED_ALIAS)
                && resolvedEntryId == null) {
            throw new IllegalArgumentException("resolvedEntryId required for resolved status");
        }
        if (status == SpeakerResolutionStatus.AMBIGUOUS && candidateEntryIds.size() < 2) {
            throw new IllegalArgumentException("ambiguous resolution requires >= 2 candidates");
        }
    }

    public static SpeakerResolution missing(UUID originalSegmentId) {
        return new SpeakerResolution(
                UUID.randomUUID(),
                originalSegmentId,
                null,
                SpeakerResolutionStatus.MISSING_SPEAKER,
                null,
                null,
                List.of());
    }

    public static SpeakerResolution unresolved(UUID originalSegmentId, String raw) {
        return new SpeakerResolution(
                UUID.randomUUID(),
                originalSegmentId,
                raw,
                SpeakerResolutionStatus.UNRESOLVED,
                null,
                null,
                List.of());
    }

    public static SpeakerResolution ambiguous(
            UUID originalSegmentId, String raw, List<UUID> candidates) {
        return new SpeakerResolution(
                UUID.randomUUID(),
                originalSegmentId,
                raw,
                SpeakerResolutionStatus.AMBIGUOUS,
                null,
                null,
                candidates);
    }

    public static SpeakerResolution exact(
            UUID originalSegmentId, String raw, UUID entryId, String canonical) {
        return new SpeakerResolution(
                UUID.randomUUID(),
                originalSegmentId,
                raw,
                SpeakerResolutionStatus.RESOLVED_EXACT,
                entryId,
                canonical,
                List.of(entryId));
    }

    public static SpeakerResolution alias(
            UUID originalSegmentId, String raw, UUID entryId, String canonical) {
        return new SpeakerResolution(
                UUID.randomUUID(),
                originalSegmentId,
                raw,
                SpeakerResolutionStatus.RESOLVED_ALIAS,
                entryId,
                canonical,
                List.of(entryId));
    }

    public boolean isResolved() {
        return status == SpeakerResolutionStatus.RESOLVED_EXACT
                || status == SpeakerResolutionStatus.RESOLVED_ALIAS;
    }

    public UUID id() {
        return id;
    }

    public UUID originalSegmentId() {
        return originalSegmentId;
    }

    public Optional<String> rawDisplayName() {
        return Optional.ofNullable(rawDisplayName);
    }

    public SpeakerResolutionStatus status() {
        return status;
    }

    public Optional<UUID> resolvedEntryId() {
        return Optional.ofNullable(resolvedEntryId);
    }

    public Optional<String> resolvedCanonical() {
        return Optional.ofNullable(resolvedCanonical);
    }

    public List<UUID> candidateEntryIds() {
        return candidateEntryIds;
    }
}
