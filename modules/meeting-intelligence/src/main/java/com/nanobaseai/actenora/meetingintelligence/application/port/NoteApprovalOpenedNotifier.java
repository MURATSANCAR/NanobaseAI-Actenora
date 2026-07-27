package com.nanobaseai.actenora.meetingintelligence.application.port;

import java.util.UUID;

/**
 * Optional side-effect hook after a note is submitted for approval.
 */
@FunctionalInterface
public interface NoteApprovalOpenedNotifier {

    void onSubmitted(
            UUID tenantId,
            UUID noteId,
            UUID meetingOccurrenceId,
            UUID approvalId,
            String approverId
    );

    static NoteApprovalOpenedNotifier noop() {
        return (tenantId, noteId, meetingOccurrenceId, approvalId, approverId) -> { };
    }
}
