package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * AI extraction candidate awaiting evidence validation / quality gate.
 * Not a persisted MeetingNote entity — mapping happens after a passing gate.
 */
public final class ValidationCandidate {

    private final String candidateKey;
    private final CandidateKind kind;
    private final String title;
    private final List<UUID> evidenceSegmentIds;
    private final Long claimedStartOffsetMs;
    private final Long claimedEndOffsetMs;
    private final String ownerParticipantId;
    private final String ownerDisplayName;
    private final String dueDateText;
    private final String amountText;
    private final BigDecimal confidence;
    private final boolean markedAsDecision;
    private final String attributedSpeakerId;
    private final boolean requiresMarkerProximity;

    private ValidationCandidate(Builder builder) {
        this.candidateKey = Objects.requireNonNull(builder.candidateKey, "candidateKey");
        this.kind = Objects.requireNonNull(builder.kind, "kind");
        this.title = Objects.requireNonNull(builder.title, "title");
        this.evidenceSegmentIds = List.copyOf(builder.evidenceSegmentIds);
        this.claimedStartOffsetMs = builder.claimedStartOffsetMs;
        this.claimedEndOffsetMs = builder.claimedEndOffsetMs;
        this.ownerParticipantId = builder.ownerParticipantId;
        this.ownerDisplayName = builder.ownerDisplayName;
        this.dueDateText = builder.dueDateText;
        this.amountText = builder.amountText;
        this.confidence = Objects.requireNonNull(builder.confidence, "confidence");
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        this.markedAsDecision = builder.markedAsDecision;
        this.attributedSpeakerId = builder.attributedSpeakerId;
        this.requiresMarkerProximity = builder.requiresMarkerProximity;
        if (candidateKey.isBlank()) {
            throw new IllegalArgumentException("candidateKey must not be blank");
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }

    public static Builder builder(String candidateKey, CandidateKind kind, String title, BigDecimal confidence) {
        return new Builder(candidateKey, kind, title, confidence);
    }

    public String candidateKey() {
        return candidateKey;
    }

    public CandidateKind kind() {
        return kind;
    }

    public String title() {
        return title;
    }

    public List<UUID> evidenceSegmentIds() {
        return evidenceSegmentIds;
    }

    public Optional<Long> claimedStartOffsetMs() {
        return Optional.ofNullable(claimedStartOffsetMs);
    }

    public Optional<Long> claimedEndOffsetMs() {
        return Optional.ofNullable(claimedEndOffsetMs);
    }

    public Optional<String> ownerParticipantId() {
        return Optional.ofNullable(ownerParticipantId);
    }

    public Optional<String> ownerDisplayName() {
        return Optional.ofNullable(ownerDisplayName);
    }

    public Optional<String> dueDateText() {
        return Optional.ofNullable(dueDateText);
    }

    public Optional<String> amountText() {
        return Optional.ofNullable(amountText);
    }

    public BigDecimal confidence() {
        return confidence;
    }

    public boolean markedAsDecision() {
        return markedAsDecision;
    }

    public Optional<String> attributedSpeakerId() {
        return Optional.ofNullable(attributedSpeakerId);
    }

    public boolean requiresMarkerProximity() {
        return requiresMarkerProximity;
    }

    public static final class Builder {
        private final String candidateKey;
        private final CandidateKind kind;
        private final String title;
        private final BigDecimal confidence;
        private List<UUID> evidenceSegmentIds = List.of();
        private Long claimedStartOffsetMs;
        private Long claimedEndOffsetMs;
        private String ownerParticipantId;
        private String ownerDisplayName;
        private String dueDateText;
        private String amountText;
        private boolean markedAsDecision;
        private String attributedSpeakerId;
        private boolean requiresMarkerProximity;

        private Builder(String candidateKey, CandidateKind kind, String title, BigDecimal confidence) {
            this.candidateKey = candidateKey;
            this.kind = kind;
            this.title = title;
            this.confidence = confidence;
        }

        public Builder evidenceSegmentIds(List<UUID> evidenceSegmentIds) {
            this.evidenceSegmentIds = Objects.requireNonNullElse(evidenceSegmentIds, List.of());
            return this;
        }

        public Builder claimedOffsets(Long startMs, Long endMs) {
            this.claimedStartOffsetMs = startMs;
            this.claimedEndOffsetMs = endMs;
            return this;
        }

        public Builder owner(String participantId, String displayName) {
            this.ownerParticipantId = participantId;
            this.ownerDisplayName = displayName;
            return this;
        }

        public Builder dueDateText(String dueDateText) {
            this.dueDateText = dueDateText;
            return this;
        }

        public Builder amountText(String amountText) {
            this.amountText = amountText;
            return this;
        }

        public Builder markedAsDecision(boolean markedAsDecision) {
            this.markedAsDecision = markedAsDecision;
            return this;
        }

        public Builder attributedSpeakerId(String attributedSpeakerId) {
            this.attributedSpeakerId = attributedSpeakerId;
            return this;
        }

        public Builder requiresMarkerProximity(boolean requiresMarkerProximity) {
            this.requiresMarkerProximity = requiresMarkerProximity;
            return this;
        }

        public ValidationCandidate build() {
            return new ValidationCandidate(this);
        }
    }
}
