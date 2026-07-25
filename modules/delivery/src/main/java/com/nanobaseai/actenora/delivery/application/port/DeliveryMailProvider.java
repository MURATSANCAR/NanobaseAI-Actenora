package com.nanobaseai.actenora.delivery.application.port;

import com.nanobaseai.actenora.delivery.domain.DeliveryRequest;
import com.nanobaseai.actenora.delivery.domain.ProviderMessage;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Mail provider port. Implementations: MailHog (dev), Microsoft Graph (prod adapter port).
 *
 * <p>{@link SendOutcome#PROVIDER_ACCEPTED} is not final delivery — only
 * {@link SendOutcome#DELIVERED} confirms receipt.
 */
public interface DeliveryMailProvider {

    SendResult send(SendCommand command);

    void validateConfiguration();

    ProviderStatus getProviderStatus();

    String providerType();

    record SendCommand(
            DeliveryRequest request,
            Optional<byte[]> pdfBytes,
            Optional<String> signedPortalUrl,
            Duration timeout
    ) {
        public SendCommand {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(pdfBytes, "pdfBytes");
            Objects.requireNonNull(signedPortalUrl, "signedPortalUrl");
            Objects.requireNonNull(timeout, "timeout");
        }
    }

    enum SendOutcome {
        PROVIDER_ACCEPTED,
        DELIVERED,
        DEFERRED,
        BOUNCED,
        FAILED,
        TIMEOUT
    }

    record SendResult(
            SendOutcome outcome,
            ProviderMessage providerMessage,
            String failureCode,
            String failureDetail
    ) {
        public SendResult {
            Objects.requireNonNull(outcome, "outcome");
        }

        public static SendResult accepted(ProviderMessage message) {
            return new SendResult(SendOutcome.PROVIDER_ACCEPTED, message, null, null);
        }

        public static SendResult delivered(ProviderMessage message) {
            return new SendResult(SendOutcome.DELIVERED, message, null, null);
        }

        public static SendResult deferred(String code, String detail) {
            return new SendResult(SendOutcome.DEFERRED, null, code, detail);
        }

        public static SendResult bounced(String code, String detail) {
            return new SendResult(SendOutcome.BOUNCED, null, code, detail);
        }

        public static SendResult failed(String code, String detail) {
            return new SendResult(SendOutcome.FAILED, null, code, detail);
        }

        public static SendResult timeout(String detail) {
            return new SendResult(SendOutcome.TIMEOUT, null, "PROVIDER_TIMEOUT", detail);
        }
    }

    record ProviderStatus(boolean healthy, String providerType, String detail) {
        public ProviderStatus {
            Objects.requireNonNull(providerType, "providerType");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
