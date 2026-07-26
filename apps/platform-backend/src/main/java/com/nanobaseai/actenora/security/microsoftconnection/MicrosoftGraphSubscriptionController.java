package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.model.SubscriptionCreateRequest;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Operator API for Graph subscriptions (no demo seed — real Graph resources only).
 */
@RestController
@RequestMapping("/api/v1/microsoft/subscriptions")
@ConditionalOnProperty(name = "actenora.microsoft-graph.enabled", havingValue = "true")
public class MicrosoftGraphSubscriptionController {

    private final MicrosoftConnectionApi microsoftConnectionApi;

    public MicrosoftGraphSubscriptionController(MicrosoftConnectionApi microsoftConnectionApi) {
        this.microsoftConnectionApi = Objects.requireNonNull(microsoftConnectionApi);
    }

    @GetMapping
    @RequiresPermission(Permission.TENANT_ADMINISTER)
    public List<SubscriptionView> list() {
        UUID tenantId = TenantSecurityContext.require().tenantId().value();
        return microsoftConnectionApi.listSubscriptions(tenantId).stream()
                .map(SubscriptionView::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.TENANT_ADMINISTER)
    public SubscriptionView create(@RequestBody CreateSubscriptionBody body) {
        UUID tenantId = TenantSecurityContext.require().tenantId().value();
        GraphSubscription created = microsoftConnectionApi.createSubscription(
                tenantId,
                new SubscriptionCreateRequest(
                        body.resource(),
                        body.changeType(),
                        body.notificationUrl(),
                        body.lifecycleNotificationUrl(),
                        body.clientState(),
                        body.expirationWindow() == null ? Duration.ofHours(48) : body.expirationWindow()
                )
        );
        return SubscriptionView.from(created);
    }

    @PostMapping("/renew-expiring")
    @RequiresPermission(Permission.OPERATIONS_MANAGE)
    public List<SubscriptionView> renewExpiring() {
        return microsoftConnectionApi.renewExpiringSubscriptions().stream()
                .map(SubscriptionView::from)
                .toList();
    }

    public record CreateSubscriptionBody(
            String resource,
            String changeType,
            String notificationUrl,
            String lifecycleNotificationUrl,
            String clientState,
            Duration expirationWindow
    ) {
    }

    public record SubscriptionView(
            UUID tenantId,
            String subscriptionId,
            String resource,
            String changeType,
            String notificationUrl,
            Instant expirationDateTime,
            String applicationId
    ) {
        static SubscriptionView from(GraphSubscription s) {
            return new SubscriptionView(
                    s.tenantId(),
                    s.subscriptionId(),
                    s.resource(),
                    s.changeType(),
                    s.notificationUrl(),
                    s.expirationDateTime(),
                    s.applicationId()
            );
        }
    }
}
