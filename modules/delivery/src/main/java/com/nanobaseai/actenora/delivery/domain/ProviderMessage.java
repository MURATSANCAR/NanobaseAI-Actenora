package com.nanobaseai.actenora.delivery.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Provider-side message reference. Acceptance by the provider is not delivery confirmation.
 */
public record ProviderMessage(
        UUID id,
        String providerType,
        String providerMessageId,
        ProviderAcceptanceStatus acceptanceStatus,
        Instant acceptedAt,
        Instant deliveredAt,
        String rawStatusCode
) {

    public enum ProviderAcceptanceStatus {
        ACCEPTED,
        REJECTED,
        UNKNOWN
    }

    public ProviderMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(providerType, "providerType");
        Objects.requireNonNull(providerMessageId, "providerMessageId");
        Objects.requireNonNull(acceptanceStatus, "acceptanceStatus");
    }

    public static ProviderMessage accepted(String providerType, String providerMessageId, Instant at) {
        return new ProviderMessage(
                UUID.randomUUID(),
                providerType,
                providerMessageId,
                ProviderAcceptanceStatus.ACCEPTED,
                at,
                null,
                "ACCEPTED"
        );
    }

    public ProviderMessage markDelivered(Instant at) {
        if (acceptanceStatus != ProviderAcceptanceStatus.ACCEPTED) {
            throw new DeliveryDomainException(
                    "INVALID_PROVIDER_STATE",
                    "cannot mark delivered before provider acceptance");
        }
        return new ProviderMessage(
                id,
                providerType,
                providerMessageId,
                acceptanceStatus,
                acceptedAt,
                Objects.requireNonNull(at, "at"),
                "DELIVERED"
        );
    }

    public boolean isDelivered() {
        return deliveredAt != null;
    }

    public Optional<Instant> deliveredAtOptional() {
        return Optional.ofNullable(deliveredAt);
    }
}
