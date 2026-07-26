package com.nanobaseai.actenora.security.delivery;

import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.api.DeliveryRequestId;
import com.nanobaseai.actenora.delivery.domain.DeliveryDomainException;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * FAZ 30 — provider delivery confirmation webhook (shared-secret auth, no user JWT).
 * Acceptance ({@code PROVIDER_ACCEPTED}) alone never implies delivery.
 */
@RestController
@RequestMapping("/api/v1/delivery/webhooks")
public class DeliveryProviderWebhookController {

    public static final String SECRET_HEADER = "X-Actenora-Delivery-Webhook-Secret";

    private final DeliveryApi deliveryApi;
    private final String webhookSecret;

    public DeliveryProviderWebhookController(
            DeliveryApi deliveryApi,
            @Value("${actenora.delivery.webhook.secret:local-delivery-webhook-secret}") String webhookSecret
    ) {
        this.deliveryApi = Objects.requireNonNull(deliveryApi, "deliveryApi");
        this.webhookSecret = Objects.requireNonNull(webhookSecret, "webhookSecret");
    }

    @PostMapping("/provider-delivered")
    public DeliveryWebhookResultView providerDelivered(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody ProviderDeliveredBody body
    ) {
        assertSecret(secret);
        Objects.requireNonNull(body, "body");
        if (body.tenantId() == null || body.deliveryRequestId() == null) {
            throw new ActenoraException("INVALID_WEBHOOK_PAYLOAD", "tenantId and deliveryRequestId are required");
        }
        String event = body.event() == null ? "delivered" : body.event().trim().toLowerCase(Locale.ROOT);
        if (!"delivered".equals(event) && !"delivery".equals(event)) {
            throw new ActenoraException(
                    "UNSUPPORTED_WEBHOOK_EVENT",
                    "Unsupported provider webhook event: " + body.event()
            );
        }

        DeliveryStatus status = deliveryApi.confirmDelivered(
                TenantId.of(body.tenantId()),
                DeliveryRequestId.of(body.deliveryRequestId())
        );
        return new DeliveryWebhookResultView(body.deliveryRequestId(), status, body.providerMessageId());
    }

    private void assertSecret(String secret) {
        if (!StringUtils.hasText(webhookSecret)) {
            throw new ActenoraException("WEBHOOK_SECRET_NOT_CONFIGURED", "Delivery webhook secret is not configured");
        }
        if (!StringUtils.hasText(secret) || !webhookSecret.equals(secret)) {
            throw new ActenoraException("WEBHOOK_UNAUTHORIZED", "Invalid delivery webhook secret");
        }
    }

    @ExceptionHandler({DeliveryDomainException.class, ActenoraException.class})
    public ResponseEntity<ProblemDetail> handleDomain(RuntimeException ex) {
        String code = ex instanceof ActenoraException actenora ? actenora.code() : "DELIVERY_ERROR";
        if (ex instanceof DeliveryDomainException domain) {
            code = domain.code();
        }
        HttpStatus status = switch (code) {
            case "WEBHOOK_UNAUTHORIZED", "WEBHOOK_SECRET_NOT_CONFIGURED" -> HttpStatus.UNAUTHORIZED;
            case "INVALID_STATUS" -> HttpStatus.CONFLICT;
            case "DELIVERY_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(code);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }

    public record ProviderDeliveredBody(
            UUID tenantId,
            UUID deliveryRequestId,
            String providerMessageId,
            String event
    ) {
    }

    public record DeliveryWebhookResultView(UUID deliveryRequestId, DeliveryStatus status, String providerMessageId) {
    }
}
