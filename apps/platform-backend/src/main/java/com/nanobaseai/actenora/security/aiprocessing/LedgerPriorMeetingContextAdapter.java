package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContext;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContextPort;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.MeetingBrief;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Maps continuity ledger briefs into extraction pipeline prior-meeting context.
 */
public final class LedgerPriorMeetingContextAdapter implements PriorMeetingContextPort {

    private final ContinuityLedgerApi ledgerApi;

    public LedgerPriorMeetingContextAdapter(ContinuityLedgerApi ledgerApi) {
        this.ledgerApi = Objects.requireNonNull(ledgerApi, "ledgerApi");
    }

    @Override
    public Optional<PriorMeetingContext> load(TenantId tenantId, UUID meetingOccurrenceId) {
        MeetingBrief brief = ledgerApi.generateBrief(tenantId, meetingOccurrenceId);
        PriorMeetingContext context = new PriorMeetingContext(
                brief.previousOccurrenceId(),
                brief.openTasks().stream().map(MeetingBrief.CarryOverItem::text).toList(),
                brief.openRisks().stream().map(MeetingBrief.CarryOverItem::text).toList(),
                brief.unresolvedQuestions().stream().map(MeetingBrief.CarryOverItem::text).toList(),
                brief.activeDecisions().stream().map(d -> d.text()).toList(),
                brief.overdueCommitments().stream().map(c -> c.text()).toList()
        );
        return context.isEmpty() ? Optional.empty() : Optional.of(context);
    }
}
