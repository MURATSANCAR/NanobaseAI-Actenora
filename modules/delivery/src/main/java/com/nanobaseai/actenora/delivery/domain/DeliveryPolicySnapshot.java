package com.nanobaseai.actenora.delivery.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable policy captured at enqueue time so later policy edits cannot change in-flight sends.
 */
public record DeliveryPolicySnapshot(
        boolean externalDeliveryAllowed,
        boolean requireApproval,
        boolean allowPdfAttachment,
        boolean sensitiveMeeting,
        boolean requireSignedPortalLink,
        int maxAttempts,
        int rateLimitPerMinute,
        Duration signedLinkTtl,
        String providerType,
        String intentChannel
) {

    public static final String PROVIDER_MAILHOG = "MAILHOG";
    public static final String PROVIDER_MICROSOFT_GRAPH = "MICROSOFT_GRAPH";
    public static final String PROVIDER_SMTP = "SMTP";

    public DeliveryPolicySnapshot {
        Objects.requireNonNull(signedLinkTtl, "signedLinkTtl");
        Objects.requireNonNull(providerType, "providerType");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (rateLimitPerMinute < 1) {
            throw new IllegalArgumentException("rateLimitPerMinute must be >= 1");
        }
        if (signedLinkTtl.isNegative() || signedLinkTtl.isZero()) {
            throw new IllegalArgumentException("signedLinkTtl must be positive");
        }
        if (requireSignedPortalLink && !sensitiveMeeting) {
            throw new IllegalArgumentException("signed portal link required only for sensitive meetings");
        }
        if (intentChannel != null && intentChannel.isBlank()) {
            intentChannel = null;
        }
    }

    /** Backward-compatible constructor for callers / tests without explicit intent. */
    public DeliveryPolicySnapshot(
            boolean externalDeliveryAllowed,
            boolean requireApproval,
            boolean allowPdfAttachment,
            boolean sensitiveMeeting,
            boolean requireSignedPortalLink,
            int maxAttempts,
            int rateLimitPerMinute,
            Duration signedLinkTtl,
            String providerType
    ) {
        this(
                externalDeliveryAllowed,
                requireApproval,
                allowPdfAttachment,
                sensitiveMeeting,
                requireSignedPortalLink,
                maxAttempts,
                rateLimitPerMinute,
                signedLinkTtl,
                providerType,
                null
        );
    }

    public static DeliveryPolicySnapshot defaults() {
        return new DeliveryPolicySnapshot(
                true,
                true,
                true,
                false,
                false,
                5,
                60,
                Duration.ofHours(24),
                PROVIDER_MAILHOG,
                null
        );
    }

    /** Organizer draft notification — no approval gate, no PDF. */
    public static DeliveryPolicySnapshot draftOrganizer() {
        return new DeliveryPolicySnapshot(
                true,
                false,
                false,
                false,
                false,
                5,
                60,
                Duration.ofHours(24),
                PROVIDER_MAILHOG,
                DeliveryIntent.DRAFT_ORGANIZER
        );
    }

    /** Meeting-ended status mail to organizer — no approval gate, no PDF, no HMAC link. */
    public static DeliveryPolicySnapshot meetingEndedOrganizer() {
        return new DeliveryPolicySnapshot(
                true,
                false,
                false,
                false,
                false,
                5,
                60,
                Duration.ofHours(24),
                PROVIDER_MAILHOG,
                DeliveryIntent.MEETING_ENDED
        );
    }

    public static DeliveryPolicySnapshot sensitiveMeetingDefaults() {
        return new DeliveryPolicySnapshot(
                true,
                true,
                false,
                true,
                true,
                5,
                30,
                Duration.ofHours(4),
                PROVIDER_MAILHOG,
                null
        );
    }

    /**
     * Explicit channel when set; otherwise DRAFT_ORGANIZER vs FINAL_EXTERNAL from requireApproval.
     */
    public String resolvedIntent() {
        if (intentChannel != null && !intentChannel.isBlank()) {
            return intentChannel;
        }
        return requireApproval
                ? DeliveryIntent.FINAL_EXTERNAL
                : DeliveryIntent.DRAFT_ORGANIZER;
    }

    public boolean shouldAttachPdf() {
        return allowPdfAttachment && !sensitiveMeeting && !requireSignedPortalLink;
    }

    public DeliveryPolicySnapshot withProvider(String providerType) {
        return new DeliveryPolicySnapshot(
                externalDeliveryAllowed,
                requireApproval,
                allowPdfAttachment,
                sensitiveMeeting,
                requireSignedPortalLink,
                maxAttempts,
                rateLimitPerMinute,
                signedLinkTtl,
                providerType,
                intentChannel
        );
    }

    public DeliveryPolicySnapshot withMaxAttempts(int maxAttempts) {
        return new DeliveryPolicySnapshot(
                externalDeliveryAllowed,
                requireApproval,
                allowPdfAttachment,
                sensitiveMeeting,
                requireSignedPortalLink,
                maxAttempts,
                rateLimitPerMinute,
                signedLinkTtl,
                providerType,
                intentChannel
        );
    }
}
