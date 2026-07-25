package com.nanobaseai.actenora.meetingintelligence;

import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.approval.application.ApprovalWorkflowService;
import com.nanobaseai.actenora.approval.infrastructure.ApprovalApiAdapter;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryApprovalRequestRepository;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryParticipantDisputeRepository;
import com.nanobaseai.actenora.approval.infrastructure.RecordingApprovalAuditPort;
import com.nanobaseai.actenora.meetingintelligence.application.MeetingNoteApprovalService;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.NoteVersionImmutableException;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteVersion;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ModelPromptSchemaProvenance;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.RecordingMeetingIntelligenceAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingNoteApprovalWorkflowTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC);

    private UUID tenantId;
    private MeetingNoteApprovalService noteService;
    private InMemoryMeetingNoteVersionRepository versionRepo;
    private RecordingMeetingIntelligenceAuditPort audit;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        versionRepo = new InMemoryMeetingNoteVersionRepository();
        audit = new RecordingMeetingIntelligenceAuditPort();
        var approvalApi = new ApprovalApiAdapter(new ApprovalWorkflowService(
                new InMemoryApprovalRequestRepository(),
                new InMemoryParticipantDisputeRepository(),
                new RecordingApprovalAuditPort(),
                CLOCK
        ));
        noteService = new MeetingNoteApprovalService(
                new InMemoryMeetingNoteRepository(),
                versionRepo,
                approvalApi,
                audit,
                CLOCK
        );
    }

    @Test
    void approvedVersionEditCreatesNewDraftAndSupersedes() {
        MeetingNote note = draft("v1");
        UUID v1 = note.currentVersionId();
        ApprovalId approvalId = noteService.submitForApproval(tenantId, note.id(), "approver", null, 0L);
        noteService.decideApproval(
                tenantId, note.id(), approvalId, "approver",
                ApprovalDecisionType.APPROVE, "ok", 1L, 0L
        );

        MeetingNoteVersion approved = versionRepo.findByIdAndTenantId(v1, note.tenantId()).orElseThrow();
        assertEquals(MeetingNoteStatus.APPROVED, approved.approvalStatus());
        assertThrows(NoteVersionImmutableException.class, approved::assertImmutable);

        MeetingNoteVersion v2 = noteService.edit(
                tenantId, note.id(), "v2", "typo fix", UUID.randomUUID(), 2L
        );
        assertEquals(MeetingNoteStatus.DRAFT, v2.approvalStatus());
        assertEquals(2, v2.versionNumber());
        assertEquals(MeetingNoteStatus.SUPERSEDED,
                versionRepo.findByIdAndTenantId(v1, note.tenantId()).orElseThrow().approvalStatus());
    }

    @Test
    void changesRequestedThenNewDraft() {
        MeetingNote note = draft("v1");
        UUID v1 = note.currentVersionId();
        ApprovalId approvalId = noteService.submitForApproval(tenantId, note.id(), "approver", null, 0L);
        noteService.decideApproval(
                tenantId, note.id(), approvalId, "approver",
                ApprovalDecisionType.REQUEST_CHANGES, "fix owners", 1L, 0L
        );
        assertEquals(MeetingNoteStatus.CHANGES_REQUESTED,
                versionRepo.findByIdAndTenantId(v1, note.tenantId()).orElseThrow().approvalStatus());

        MeetingNoteVersion v2 = noteService.edit(
                tenantId, note.id(), "v1 fixed", "addressed", UUID.randomUUID(), 2L
        );
        assertEquals(MeetingNoteStatus.DRAFT, v2.approvalStatus());
        assertTrue(audit.timelineFor(note.id()).stream()
                .anyMatch(e -> e.action().equals("NOTE_APPROVAL_CHANGES_REQUESTED")));
    }

    @Test
    void pendingApprovalVersionIsImmutable() {
        MeetingNote note = draft("v1");
        noteService.submitForApproval(tenantId, note.id(), "approver", null, 0L);
        assertThrows(NoteVersionImmutableException.class, () ->
                noteService.edit(tenantId, note.id(), "sneaky", "no", UUID.randomUUID(), 1L)
        );
    }

    private MeetingNote draft(String summary) {
        return noteService.createDraft(
                tenantId,
                UUID.randomUUID(),
                summary,
                ModelPromptSchemaProvenance.of("m", "p", "s", 0.8)
        );
    }
}
