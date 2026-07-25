package com.nanobaseai.actenora.meeting.application.relation;

import com.nanobaseai.actenora.meeting.domain.continuity.ImmutableEventIdentity;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceContinuityKey;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceIdentitySnapshot;
import com.nanobaseai.actenora.meeting.domain.relation.CrossTenantRelationException;
import com.nanobaseai.actenora.meeting.domain.relation.CyclicRelationException;
import com.nanobaseai.actenora.meeting.domain.relation.DuplicateRelationException;
import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelation;
import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelationSuggestion;
import com.nanobaseai.actenora.meeting.domain.relation.RelationType;
import com.nanobaseai.actenora.meeting.domain.relation.SuggestionStatus;
import com.nanobaseai.actenora.meeting.infrastructure.relation.InMemoryMeetingRelationRepository;
import com.nanobaseai.actenora.meeting.infrastructure.relation.InMemoryMeetingRelationSuggestionRepository;
import com.nanobaseai.actenora.meeting.infrastructure.relation.InMemoryOccurrenceContinuityPort;
import com.nanobaseai.actenora.meeting.infrastructure.relation.RecordingRelationAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingRelationServiceTest {

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC);

    private InMemoryOccurrenceContinuityPort occurrences;
    private InMemoryMeetingRelationRepository relations;
    private InMemoryMeetingRelationSuggestionRepository suggestions;
    private RecordingRelationAuditPort audit;
    private MeetingRelationService service;

    private OccurrenceIdentitySnapshot occA1;
    private OccurrenceIdentitySnapshot occA2;
    private OccurrenceIdentitySnapshot occA3;
    private OccurrenceIdentitySnapshot occB1;

    @BeforeEach
    void setUp() {
        occurrences = new InMemoryOccurrenceContinuityPort();
        relations = new InMemoryMeetingRelationRepository();
        suggestions = new InMemoryMeetingRelationSuggestionRepository();
        audit = new RecordingRelationAuditPort();
        service = new MeetingRelationService(relations, suggestions, occurrences, audit, clock);

        UUID series = UUID.randomUUID();
        occA1 = occurrence(tenantA, series, "imm-1", "2026-07-01T10:00:00Z", "Week 1");
        occA2 = occurrence(tenantA, series, "imm-2", "2026-07-08T10:00:00Z", "Week 2");
        occA3 = occurrence(tenantA, series, "imm-3", "2026-07-15T10:00:00Z", "Week 3");
        occB1 = occurrence(tenantB, UUID.randomUUID(), "imm-b", "2026-07-01T10:00:00Z", "Other tenant");

        occurrences.put(occA1);
        occurrences.put(occA2);
        occurrences.put(occA3);
        occurrences.put(occB1);
    }

    @Test
    void duplicateRelationIsRejectedAndAuditedOnlyOnSuccess() {
        service.createManualRelation(new CreateManualRelationCommand(
                tenantA, occA1.occurrenceId(), occA2.occurrenceId(), RelationType.RELATED, "user-1"
        ));

        assertThrows(DuplicateRelationException.class, () ->
                service.createManualRelation(new CreateManualRelationCommand(
                        tenantA, occA2.occurrenceId(), occA1.occurrenceId(), RelationType.RELATED, "user-1"
                )));

        assertEquals(1, relations.findAllByTenant(tenantA).size());
        assertEquals(1, audit.entries().stream().filter(e -> e.action().equals("RELATION_CREATED")).count());
    }

    @Test
    void cyclicFollowUpIsRejected() {
        service.createManualRelation(new CreateManualRelationCommand(
                tenantA, occA1.occurrenceId(), occA2.occurrenceId(), RelationType.FOLLOW_UP, "user-1"
        ));
        service.createManualRelation(new CreateManualRelationCommand(
                tenantA, occA2.occurrenceId(), occA3.occurrenceId(), RelationType.FOLLOW_UP, "user-1"
        ));

        assertThrows(CyclicRelationException.class, () ->
                service.createManualRelation(new CreateManualRelationCommand(
                        tenantA, occA3.occurrenceId(), occA1.occurrenceId(), RelationType.FOLLOW_UP, "user-1"
                )));
    }

    @Test
    void suggestionDoesNotCreateRelationUntilApproved() {
        MeetingRelationSuggestion suggestion = service.recordSuggestion(new RecordRelationSuggestionCommand(
                tenantA,
                occA1.occurrenceId(),
                occA2.occurrenceId(),
                RelationType.FOLLOW_UP,
                new BigDecimal("0.91"),
                "Shared attendees and agenda continuity"
        ));

        assertEquals(SuggestionStatus.PENDING, suggestion.status());
        assertTrue(relations.findAllByTenant(tenantA).isEmpty());
        assertEquals("0.91", suggestion.confidence().toPlainString());
        assertEquals("Shared attendees and agenda continuity", suggestion.reason());

        Optional<MeetingRelation> created = service.decideSuggestion(
                new DecideSuggestionCommand(tenantA, suggestion.id(), true, "approver-1")
        );

        assertTrue(created.isPresent());
        assertEquals(RelationType.AI_SUGGESTED, created.get().relationType());
        assertEquals(1, relations.findAllByTenant(tenantA).size());
        assertTrue(audit.entries().stream().anyMatch(e -> e.action().equals("RELATION_SUGGESTION_APPROVED")));
    }

    @Test
    void suggestionRejectDoesNotCreateRelation() {
        MeetingRelationSuggestion suggestion = service.recordSuggestion(new RecordRelationSuggestionCommand(
                tenantA,
                occA1.occurrenceId(),
                occA2.occurrenceId(),
                RelationType.RELATED,
                new BigDecimal("0.40"),
                "Weak title similarity"
        ));

        Optional<MeetingRelation> created = service.decideSuggestion(
                new DecideSuggestionCommand(tenantA, suggestion.id(), false, "approver-1")
        );

        assertTrue(created.isEmpty());
        assertTrue(relations.findAllByTenant(tenantA).isEmpty());
        assertEquals(SuggestionStatus.REJECTED,
                suggestions.findById(tenantA, suggestion.id()).orElseThrow().status());
        assertTrue(audit.entries().stream().anyMatch(e -> e.action().equals("RELATION_SUGGESTION_REJECTED")));
    }

    @Test
    void tenantIsolationBlocksCrossTenantRelation() {
        assertThrows(CrossTenantRelationException.class, () ->
                service.createManualRelation(new CreateManualRelationCommand(
                        tenantA, occA1.occurrenceId(), occB1.occurrenceId(), RelationType.MANUAL, "user-1"
                )));
        assertTrue(relations.findAllByTenant(tenantA).isEmpty());
        assertTrue(relations.findAllByTenant(tenantB).isEmpty());
    }

    @Test
    void previousNextProjectionUsesSeriesOrder() {
        var projection = service.projectContinuity(tenantA, occA2.occurrenceId());
        assertEquals(occA1.occurrenceId(), projection.previousOccurrenceId().orElseThrow());
        assertEquals(occA3.occurrenceId(), projection.nextOccurrenceId().orElseThrow());
    }

    @Test
    void previousNextProjectionPrefersExplicitFollowUp() {
        OccurrenceIdentitySnapshot standalone = occurrence(
                tenantA, UUID.randomUUID(), "imm-x", "2026-08-01T10:00:00Z", "Ad-hoc"
        );
        occurrences.put(standalone);

        service.createManualRelation(new CreateManualRelationCommand(
                tenantA, occA1.occurrenceId(), standalone.occurrenceId(), RelationType.FOLLOW_UP, "user-1"
        ));

        var projection = service.projectContinuity(tenantA, standalone.occurrenceId());
        assertEquals(occA1.occurrenceId(), projection.previousOccurrenceId().orElseThrow());
    }

    private OccurrenceIdentitySnapshot occurrence(
            UUID tenantId,
            UUID seriesId,
            String immutableId,
            String start,
            String title
    ) {
        Instant original = Instant.parse(start);
        return new OccurrenceIdentitySnapshot(
                UUID.randomUUID(),
                tenantId,
                seriesId,
                UUID.randomUUID(),
                ImmutableEventIdentity.of(immutableId),
                new OccurrenceContinuityKey("series-master-" + seriesId, "ical-" + seriesId, original),
                "https://teams.microsoft.com/l/meetup-join/shared",
                title,
                original,
                original.plusSeconds(3600)
        );
    }
}
