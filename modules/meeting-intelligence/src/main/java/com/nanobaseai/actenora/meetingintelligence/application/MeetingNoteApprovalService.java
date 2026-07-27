package com.nanobaseai.actenora.meetingintelligence.application;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.api.ApprovalRequestView;
import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteLedgerPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingIntelligenceAuditPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.NoteApprovalOpenedNotifier;
import com.nanobaseai.actenora.meetingintelligence.application.port.NoteArtifactStoragePort;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.MeetingNoteNotFoundException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.NoteVersionImmutableException;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteVersion;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ModelPromptSchemaProvenance;
import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteReviewStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates meeting-note approval/versioning with the Approval BC (FAZ 18).
 */
public final class MeetingNoteApprovalService {

    private final MeetingNoteRepository noteRepository;
    private final MeetingNoteVersionRepository versionRepository;
    private final ApprovalApi approvalApi;
    private final MeetingIntelligenceAuditPort auditPort;
    private final ApprovedNoteLedgerPort approvedNoteLedgerPort;
    private final NoteArtifactStoragePort noteArtifactStorage;
    private final NoteApprovalOpenedNotifier approvalOpenedNotifier;
    private final Clock clock;

    public MeetingNoteApprovalService(
            MeetingNoteRepository noteRepository,
            MeetingNoteVersionRepository versionRepository,
            ApprovalApi approvalApi,
            MeetingIntelligenceAuditPort auditPort,
            Clock clock
    ) {
        this(
                noteRepository,
                versionRepository,
                approvalApi,
                auditPort,
                (tenantId, meetingOccurrenceId, noteId, noteVersionId) -> {
                },
                NoteArtifactStoragePort.noop(),
                NoteApprovalOpenedNotifier.noop(),
                clock
        );
    }

    public MeetingNoteApprovalService(
            MeetingNoteRepository noteRepository,
            MeetingNoteVersionRepository versionRepository,
            ApprovalApi approvalApi,
            MeetingIntelligenceAuditPort auditPort,
            ApprovedNoteLedgerPort approvedNoteLedgerPort,
            Clock clock
    ) {
        this(
                noteRepository,
                versionRepository,
                approvalApi,
                auditPort,
                approvedNoteLedgerPort,
                NoteArtifactStoragePort.noop(),
                NoteApprovalOpenedNotifier.noop(),
                clock
        );
    }

    public MeetingNoteApprovalService(
            MeetingNoteRepository noteRepository,
            MeetingNoteVersionRepository versionRepository,
            ApprovalApi approvalApi,
            MeetingIntelligenceAuditPort auditPort,
            ApprovedNoteLedgerPort approvedNoteLedgerPort,
            NoteArtifactStoragePort noteArtifactStorage,
            Clock clock
    ) {
        this(
                noteRepository,
                versionRepository,
                approvalApi,
                auditPort,
                approvedNoteLedgerPort,
                noteArtifactStorage,
                NoteApprovalOpenedNotifier.noop(),
                clock
        );
    }

    public MeetingNoteApprovalService(
            MeetingNoteRepository noteRepository,
            MeetingNoteVersionRepository versionRepository,
            ApprovalApi approvalApi,
            MeetingIntelligenceAuditPort auditPort,
            ApprovedNoteLedgerPort approvedNoteLedgerPort,
            NoteArtifactStoragePort noteArtifactStorage,
            NoteApprovalOpenedNotifier approvalOpenedNotifier,
            Clock clock
    ) {
        this.noteRepository = Objects.requireNonNull(noteRepository, "noteRepository");
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository");
        this.approvalApi = Objects.requireNonNull(approvalApi, "approvalApi");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort");
        this.approvedNoteLedgerPort = Objects.requireNonNull(approvedNoteLedgerPort, "approvedNoteLedgerPort");
        this.noteArtifactStorage = Objects.requireNonNull(noteArtifactStorage, "noteArtifactStorage");
        this.approvalOpenedNotifier = Objects.requireNonNull(approvalOpenedNotifier, "approvalOpenedNotifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public MeetingNote createDraft(
            UUID tenantId,
            UUID meetingOccurrenceId,
            String content,
            ModelPromptSchemaProvenance provenance
    ) {
        Instant now = clock.instant();
        TenantId tid = TenantId.of(tenantId);
        MeetingNote note = MeetingNote.create(tid, meetingOccurrenceId, NoteReviewStatus.ACTIVE, now);
        MeetingNoteVersion version = note.attachInitialAiVersion(content, provenance, now);
        noteRepository.save(note);
        versionRepository.save(version);
        noteArtifactStorage.storeDraft(
                tid,
                meetingOccurrenceId,
                note.id(),
                version.versionNumber(),
                content == null ? "{}" : content
        );
        auditPort.record(
                tenantId, "system", "NOTE_DRAFT_CREATED", "MeetingNote", note.id(),
                Map.of("versionId", version.id().toString(), "status", version.approvalStatus().name()), now
        );
        return note;
    }

    public ApprovalId submitForApproval(
            UUID tenantId,
            UUID noteId,
            String approverId,
            Instant expiresAt,
            long expectedNoteVersion
    ) {
        TenantId tid = TenantId.of(tenantId);
        MeetingNote note = requireNote(tid, noteId);
        note.assertVersion(expectedNoteVersion);
        MeetingNoteVersion current = requireCurrentVersion(tid, note);
        Instant now = clock.instant();

        current.transitionApprovalStatus(MeetingNoteStatus.PENDING_APPROVAL);
        versionRepository.save(current);
        bumpNoteVersion(note, expectedNoteVersion, now);
        noteRepository.save(note);

        ApprovalId approvalId = approvalApi.openSingleStage(
                tenantId,
                ApprovalSubjectType.MEETING_NOTE_VERSION,
                current.id(),
                approverId,
                expiresAt
        );
        auditPort.record(
                tenantId, approverId, "NOTE_SUBMITTED_FOR_APPROVAL", "MeetingNote", note.id(),
                Map.of(
                        "versionId", current.id().toString(),
                        "approvalId", approvalId.value().toString(),
                        "status", MeetingNoteStatus.PENDING_APPROVAL.name()
                ),
                now
        );
        try {
            approvalOpenedNotifier.onSubmitted(
                    tenantId,
                    note.id(),
                    note.meetingOccurrenceId(),
                    approvalId.value(),
                    approverId
            );
        } catch (RuntimeException ignored) {
            // Notification must not fail approval submit.
        }
        return approvalId;
    }

    public MeetingNote decideApproval(
            UUID tenantId,
            UUID noteId,
            ApprovalId approvalId,
            String actorId,
            ApprovalDecisionType decisionType,
            String comment,
            long expectedNoteVersion,
            long expectedApprovalVersion
    ) {
        TenantId tid = TenantId.of(tenantId);
        MeetingNote note = requireNote(tid, noteId);
        note.assertVersion(expectedNoteVersion);
        MeetingNoteVersion current = requireCurrentVersion(tid, note);
        Instant now = clock.instant();

        ApprovalRequestStatus status = approvalApi.decide(
                tenantId, approvalId, actorId, decisionType, comment, expectedApprovalVersion
        );

        MeetingNoteStatus target = switch (status) {
            case GRANTED -> MeetingNoteStatus.APPROVED;
            case DENIED -> MeetingNoteStatus.REJECTED;
            case CHANGES_REQUESTED -> MeetingNoteStatus.CHANGES_REQUESTED;
            default -> throw new IllegalStateException("unexpected approval status: " + status);
        };
        current.transitionApprovalStatus(target);
        versionRepository.save(current);
        bumpNoteVersion(note, expectedNoteVersion, now);
        noteRepository.save(note);

        if (status == ApprovalRequestStatus.GRANTED) {
            approvedNoteLedgerPort.append(
                    tid,
                    note.meetingOccurrenceId(),
                    note.id(),
                    current.id()
            );
            noteArtifactStorage.storeApproved(
                    tid,
                    note.meetingOccurrenceId(),
                    note.id(),
                    current.versionNumber(),
                    current.executiveSummary() == null ? "{}" : current.executiveSummary()
            );
        }

        auditPort.record(
                tenantId, actorId, "NOTE_APPROVAL_" + status.name(), "MeetingNote", note.id(),
                Map.of(
                        "versionId", current.id().toString(),
                        "approvalId", approvalId.value().toString(),
                        "comment", comment == null ? "" : comment,
                        "noteStatus", current.approvalStatus().name()
                ),
                now
        );
        return note;
    }

    /**
     * Resolves note from approval subject ({@link ApprovalSubjectType#MEETING_NOTE_VERSION}) then decides.
     */
    public MeetingNote decideByApprovalId(
            UUID tenantId,
            ApprovalId approvalId,
            String actorId,
            ApprovalDecisionType decisionType,
            String comment,
            Long expectedNoteVersion,
            Long expectedApprovalVersion
    ) {
        ApprovalRequestView approval = approvalApi.get(tenantId, approvalId)
                .orElseThrow(() -> new ActenoraException(
                        "INTELLIGENCE_RESOURCE_NOT_FOUND",
                        "Approval not found: " + approvalId.value()
                ));
        if (approval.subjectType() != ApprovalSubjectType.MEETING_NOTE_VERSION) {
            throw new ActenoraException(
                    "INVALID_APPROVAL_SUBJECT",
                    "Approval subject is not a meeting note version: " + approval.subjectType()
            );
        }
        TenantId tid = TenantId.of(tenantId);
        MeetingNoteVersion version = versionRepository
                .findByIdAndTenantId(approval.subjectId(), tid)
                .orElseThrow(() -> new MeetingNoteNotFoundException(approval.subjectId()));
        MeetingNote note = requireNote(tid, version.noteId());
        long noteVersion = expectedNoteVersion == null ? note.version() : expectedNoteVersion;
        long approvalVersion = expectedApprovalVersion == null ? approval.version() : expectedApprovalVersion;
        return decideApproval(
                tenantId,
                note.id(),
                approvalId,
                actorId,
                decisionType,
                comment,
                noteVersion,
                approvalVersion
        );
    }

    /**
     * Edits create a new draft version. Approved/rejected versions are superseded — never mutated.
     */
    public MeetingNoteVersion edit(
            UUID tenantId,
            UUID noteId,
            String newContent,
            String correctionReason,
            UUID editorUserId,
            long expectedNoteVersion
    ) {
        TenantId tid = TenantId.of(tenantId);
        MeetingNote note = requireNote(tid, noteId);
        MeetingNoteVersion current = requireCurrentVersion(tid, note);
        Instant now = clock.instant();

        if (current.approvalStatus() == MeetingNoteStatus.PENDING_APPROVAL) {
            throw new NoteVersionImmutableException();
        }
        if (current.approvalStatus() == MeetingNoteStatus.APPROVED
                || current.approvalStatus() == MeetingNoteStatus.REJECTED
                || current.approvalStatus() == MeetingNoteStatus.CHANGES_REQUESTED) {
            current.transitionApprovalStatus(MeetingNoteStatus.SUPERSEDED);
            versionRepository.save(current);
        } else if (current.isContentLocked()) {
            throw new NoteVersionImmutableException();
        }

        MeetingNoteVersion next = note.appendHumanEdit(
                newContent,
                correctionReason,
                editorUserId,
                current.provenance(),
                expectedNoteVersion,
                now
        );
        noteRepository.save(note);
        versionRepository.save(next);
        auditPort.record(
                tenantId, editorUserId.toString(), "NOTE_EDITED", "MeetingNote", note.id(),
                Map.of(
                        "versionId", next.id().toString(),
                        "versionNumber", next.versionNumber(),
                        "status", next.approvalStatus().name(),
                        "supersededVersionId", current.id().toString()
                ),
                now
        );
        return next;
    }

    public MeetingNoteVersion applyAcceptedDisputeAsNewDraft(
            UUID tenantId,
            UUID noteId,
            UUID disputeId,
            String resolverId,
            UUID resolverUserId,
            String correctionReason,
            long expectedNoteVersion
    ) {
        String proposed = approvalApi.acceptDispute(tenantId, disputeId, resolverId);
        return edit(tenantId, noteId, proposed, correctionReason, resolverUserId, expectedNoteVersion);
    }

    public MeetingNote requireNote(TenantId tenantId, UUID noteId) {
        return noteRepository
                .findByIdAndTenantId(noteId, tenantId)
                .orElseThrow(() -> new MeetingNoteNotFoundException(noteId));
    }

    private MeetingNoteVersion requireCurrentVersion(TenantId tenantId, MeetingNote note) {
        return versionRepository
                .findByIdAndTenantId(note.currentVersionId(), tenantId)
                .orElseThrow(() -> new MeetingNoteNotFoundException(note.id()));
    }

    private void bumpNoteVersion(MeetingNote note, long expectedVersion, Instant now) {
        note.touchOptimisticLock(expectedVersion, now);
    }
}
