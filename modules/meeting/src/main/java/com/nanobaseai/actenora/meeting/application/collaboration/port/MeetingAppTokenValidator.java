package com.nanobaseai.actenora.meeting.application.collaboration.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Validates backend tokens for the Teams Meeting App.
 * Teams client context is never sufficient on its own.
 */
public interface MeetingAppTokenValidator {

    ValidatedMeetingPrincipal validate(String authorizationHeader, UntrustedTeamsContext teamsContext);

    record UntrustedTeamsContext(
            String teamsMeetingId,
            String chatId,
            String claimedTenantId,
            String claimedUserId
    ) {
    }

    record ValidatedMeetingPrincipal(
            TenantId tenantId,
            UUID userId,
            String teamsMeetingId,
            String tokenId
    ) {
        public ValidatedMeetingPrincipal {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(tokenId, "tokenId");
        }
    }
}
