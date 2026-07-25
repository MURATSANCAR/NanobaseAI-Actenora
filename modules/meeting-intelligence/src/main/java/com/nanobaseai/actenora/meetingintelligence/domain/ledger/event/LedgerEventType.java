package com.nanobaseai.actenora.meetingintelligence.domain.ledger.event;

/**
 * Event types that drive decision ledger / commitment tracker / continuity projections.
 * Naming: {@code meetingintelligence.<Name>.v1}
 */
public enum LedgerEventType {
    DECISION_RECORDED("meetingintelligence.DecisionRecorded.v1"),
    DECISION_SUPERSEDED("meetingintelligence.DecisionSuperseded.v1"),
    COMMITMENT_RECORDED("meetingintelligence.CommitmentRecorded.v1"),
    COMMITMENT_STATE_CHANGED("meetingintelligence.CommitmentStateChanged.v1"),
    ACTION_ITEM_RECORDED("meetingintelligence.ActionItemRecorded.v1"),
    ACTION_ITEM_STATE_CHANGED("meetingintelligence.ActionItemStateChanged.v1"),
    RISK_RECORDED("meetingintelligence.RiskRecorded.v1"),
    RISK_CLOSED("meetingintelligence.RiskClosed.v1"),
    OPEN_QUESTION_RECORDED("meetingintelligence.OpenQuestionRecorded.v1"),
    OPEN_QUESTION_RESOLVED("meetingintelligence.OpenQuestionResolved.v1"),
    OCCURRENCE_CONTINUITY_LINKED("meetingintelligence.OccurrenceContinuityLinked.v1"),
    FOLLOW_UP_LINKED("meetingintelligence.FollowUpLinked.v1"),
    RELATION_SUGGESTION_RECORDED("meetingintelligence.RelationSuggestionRecorded.v1"),
    RELATION_SUGGESTION_DECIDED("meetingintelligence.RelationSuggestionDecided.v1"),
    CONTRADICTION_PROPOSED("meetingintelligence.ContradictionProposed.v1"),
    CONTRADICTION_DECIDED("meetingintelligence.ContradictionDecided.v1");

    private final String wireType;

    LedgerEventType(String wireType) {
        this.wireType = wireType;
    }

    public String wireType() {
        return wireType;
    }

    public static LedgerEventType fromWireType(String wireType) {
        for (LedgerEventType type : values()) {
            if (type.wireType.equals(wireType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ledger event type: " + wireType);
    }
}
