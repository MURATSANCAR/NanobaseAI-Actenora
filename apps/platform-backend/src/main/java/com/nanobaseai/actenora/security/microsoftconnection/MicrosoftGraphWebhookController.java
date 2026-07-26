package com.nanobaseai.actenora.security.microsoftconnection;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphChangeNotification;
import com.nanobaseai.actenora.microsoftconnection.application.model.LifecycleNotification;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * FAZ 32 — Microsoft Graph change/lifecycle notification webhook (platform binding).
 *
 * <p>Two concerns on the single Graph notification URL:
 * <ol>
 *   <li>subscription validation handshake — Graph POSTs {@code ?validationToken=...} and expects
 *       the raw token echoed back as {@code text/plain} within 10s;</li>
 *   <li>notification delivery — each item is authenticated by {@code clientState} (shared secret
 *       set at subscription creation, not a user JWT) and dispatched with per-notification
 *       idempotency through {@link MicrosoftConnectionApi}.</li>
 * </ol>
 * Only registered when {@code actenora.microsoft-graph.enabled=true} (same gate as the module).
 */
@RestController
@RequestMapping("/api/v1/microsoft/webhooks")
@ConditionalOnProperty(name = "actenora.microsoft-graph.enabled", havingValue = "true")
public class MicrosoftGraphWebhookController {

    private final MicrosoftConnectionApi microsoftConnectionApi;
    private final String expectedClientState;

    public MicrosoftGraphWebhookController(
            MicrosoftConnectionApi microsoftConnectionApi,
            @Value("${actenora.microsoft-graph.webhook.client-state:local-graph-client-state}")
            String expectedClientState
    ) {
        this.microsoftConnectionApi = Objects.requireNonNull(microsoftConnectionApi, "microsoftConnectionApi");
        this.expectedClientState = Objects.requireNonNull(expectedClientState, "expectedClientState");
    }

    @PostMapping("/graph-notifications")
    public ResponseEntity<?> notifications(
            @RequestParam(value = "validationToken", required = false) String validationToken,
            @RequestBody(required = false) GraphNotificationBatch body
    ) {
        if (StringUtils.hasText(validationToken)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(validationToken);
        }

        if (body == null || body.value() == null || body.value().isEmpty()) {
            throw new ActenoraException("INVALID_WEBHOOK_PAYLOAD", "notification batch is empty");
        }

        int received = 0;
        int processed = 0;
        int duplicates = 0;
        int rejected = 0;

        for (GraphNotificationItem item : body.value()) {
            if (item == null) {
                continue;
            }
            received++;
            if (!clientStateMatches(item.clientState())) {
                rejected++;
                continue;
            }
            boolean firstTime = item.isLifecycle()
                    ? dispatchLifecycle(item)
                    : dispatchChange(item);
            if (firstTime) {
                processed++;
            } else {
                duplicates++;
            }
        }

        return ResponseEntity.accepted()
                .body(new GraphWebhookResultView(received, processed, duplicates, rejected));
    }

    private boolean dispatchChange(GraphNotificationItem item) {
        if (!StringUtils.hasText(item.subscriptionId())) {
            throw new ActenoraException("INVALID_WEBHOOK_PAYLOAD", "subscriptionId is required");
        }
        GraphChangeNotification notification = new GraphChangeNotification(
                item.changeNotificationId(),
                item.subscriptionId(),
                item.changeType(),
                item.resource(),
                item.resourceDataId(),
                item.clientState(),
                item.tenantId()
        );
        // Handler is a hook for downstream ingestion (transcript polling); dedup/claim is the
        // durable guarantee here, so an empty handler still safely acknowledges the notification.
        return microsoftConnectionApi.onChangeNotification(notification, n -> { });
    }

    private boolean dispatchLifecycle(GraphNotificationItem item) {
        if (!StringUtils.hasText(item.subscriptionId())) {
            throw new ActenoraException("INVALID_WEBHOOK_PAYLOAD", "subscriptionId is required");
        }
        LifecycleNotification notification = new LifecycleNotification(
                item.lifecycleNotificationId(),
                item.subscriptionId(),
                item.lifecycleEvent(),
                item.clientState(),
                item.tenantId()
        );
        return microsoftConnectionApi.onLifecycleNotification(notification, n -> { });
    }

    private boolean clientStateMatches(String clientState) {
        if (!StringUtils.hasText(expectedClientState)) {
            return true;
        }
        return expectedClientState.equals(clientState);
    }

    @ExceptionHandler(ActenoraException.class)
    public ResponseEntity<ProblemDetail> handle(ActenoraException ex) {
        HttpStatus status = switch (ex.code()) {
            case "INVALID_WEBHOOK_PAYLOAD" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(ex.code());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(status).body(problem);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphNotificationBatch(List<GraphNotificationItem> value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphNotificationItem(
            String subscriptionId,
            String changeType,
            String resource,
            GraphResourceData resourceData,
            String lifecycleEvent,
            String clientState,
            String tenantId,
            String subscriptionExpirationDateTime
    ) {

        boolean isLifecycle() {
            return StringUtils.hasText(lifecycleEvent);
        }

        String resourceDataId() {
            if (resourceData == null) {
                return null;
            }
            return StringUtils.hasText(resourceData.id()) ? resourceData.id() : resourceData.odataId();
        }

        String changeNotificationId() {
            String resourcePart = StringUtils.hasText(resourceDataId()) ? resourceDataId() : resource;
            return subscriptionId + "::" + changeType + "::" + resourcePart;
        }

        String lifecycleNotificationId() {
            return subscriptionId + "::lifecycle::" + lifecycleEvent;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphResourceData(
            @JsonProperty("@odata.id") String odataId,
            String id
    ) {
    }

    public record GraphWebhookResultView(int received, int processed, int duplicates, int rejected) {
    }
}
