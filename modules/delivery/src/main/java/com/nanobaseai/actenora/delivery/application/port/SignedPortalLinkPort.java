package com.nanobaseai.actenora.delivery.application.port;

import com.nanobaseai.actenora.delivery.domain.SignedPortalLink;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues and validates HMAC-signed portal links for sensitive meetings.
 */
public interface SignedPortalLinkPort {

    SignedPortalLink issue(
            TenantId tenantId,
            UUID noteVersionId,
            UUID deliveryRequestId,
            String recipientEmail,
            Duration ttl,
            Instant now
    );

    boolean isValid(SignedPortalLink link, Instant now);

    boolean verifyToken(
            TenantId tenantId,
            UUID noteVersionId,
            UUID deliveryRequestId,
            String recipientEmail,
            String rawToken,
            Instant expiresAt,
            Instant now
    );
}
