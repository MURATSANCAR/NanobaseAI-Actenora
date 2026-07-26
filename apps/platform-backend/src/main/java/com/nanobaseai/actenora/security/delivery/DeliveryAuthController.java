package com.nanobaseai.actenora.security.delivery;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.approval.api.ApprovalRequestView;
import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.api.DeliveryOrderView;
import com.nanobaseai.actenora.delivery.api.DeliveryRequestId;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryCommand;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryResult;
import com.nanobaseai.actenora.delivery.application.worker.DeliveryWorker;
import com.nanobaseai.actenora.delivery.domain.DeliveryDomainException;
import com.nanobaseai.actenora.delivery.domain.DeliveryOrderStatus;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.DeliveryRecipient;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;
import com.nanobaseai.actenora.delivery.domain.RecipientKind;
import com.nanobaseai.actenora.delivery.domain.exception.ExternalDeliveryBlockedException;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.domain.Permission;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Auth-bound delivery HTTP surface (FAZ 19–20 orders/enqueue + FAZ 23 confirmDelivered).
 */
@RestController
@RequestMapping("/api/v1")
public class DeliveryAuthController {

    private final DeliveryApi deliveryApi;
    private final ApprovalApi approvalApi;
    private final DeliveryWorker deliveryWorker;
    private final IdentityApi identityApi;

    public DeliveryAuthController(
            DeliveryApi deliveryApi,
            ApprovalApi approvalApi,
            DeliveryWorker deliveryWorker,
            IdentityApi identityApi
    ) {
        this.deliveryApi = Objects.requireNonNull(deliveryApi, "deliveryApi");
        this.approvalApi = Objects.requireNonNull(approvalApi, "approvalApi");
        this.deliveryWorker = Objects.requireNonNull(deliveryWorker, "deliveryWorker");
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
    }

    @PostMapping("/delivery/orders")
    @RequiresPermission(Permission.DELIVERY_MANAGE)
    public DeliveryOrderHttpView requestOrder(@RequestBody RequestDeliveryOrderBody body) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.DELIVERY_MANAGE);
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(body.approvalId(), "approvalId");

        UUID tenantId = principal.tenantId().value();
        ApprovalId approvalId = ApprovalId.of(body.approvalId());
        ApprovalRequestView approval = approvalApi.get(tenantId, approvalId)
                .orElseThrow(() -> new ActenoraException(
                        "INTELLIGENCE_RESOURCE_NOT_FOUND",
                        "Approval not found: " + body.approvalId()
                ));
        if (approval.subjectType() != ApprovalSubjectType.MEETING_NOTE_VERSION) {
            throw new ActenoraException(
                    "INVALID_APPROVAL_SUBJECT",
                    "Approval subject is not a meeting note version"
            );
        }
        UUID noteVersionId = body.noteVersionId() == null ? approval.subjectId() : body.noteVersionId();
        if (!noteVersionId.equals(approval.subjectId())) {
            throw new ActenoraException(
                    "APPROVAL_SUBJECT_MISMATCH",
                    "noteVersionId does not match approval subject"
            );
        }
        String channel = body.channel() == null || body.channel().isBlank() ? "email" : body.channel().trim();
        DeliveryOrderView order = deliveryApi.requestExternalDelivery(
                tenantId, approvalId, noteVersionId, channel);
        return DeliveryOrderHttpView.from(order);
    }

    @GetMapping("/delivery/orders/{orderId}")
    @RequiresPermission(Permission.DELIVERY_MANAGE)
    public DeliveryOrderHttpView getOrder(@PathVariable UUID orderId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.DELIVERY_MANAGE);
        DeliveryOrderView order = requireOrder(principal.tenantId().value(), orderId);
        return DeliveryOrderHttpView.from(order);
    }

    @PostMapping("/delivery/orders/{orderId}/enqueue")
    @RequiresPermission(Permission.DELIVERY_MANAGE)
    public EnqueueHttpView enqueue(
            @PathVariable UUID orderId,
            @RequestBody EnqueueDeliveryBody body
    ) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.DELIVERY_MANAGE);
        Objects.requireNonNull(body, "body");

        DeliveryOrderView order = requireOrder(principal.tenantId().value(), orderId);
        if (order.status() != DeliveryOrderStatus.READY) {
            throw new ActenoraException(
                    "DELIVERY_ORDER_NOT_READY",
                    "Delivery order is not READY: " + order.status()
            );
        }
        List<DeliveryRecipient> recipients = mapRecipients(body.recipients());
        String subject = body.subject() == null || body.subject().isBlank()
                ? "Meeting notes"
                : body.subject().trim();
        String bodyText = body.bodyText() == null || body.bodyText().isBlank()
                ? "Please review the approved meeting notes."
                : body.bodyText().trim();

        EnqueueDeliveryResult result = deliveryApi.enqueue(new EnqueueDeliveryCommand(
                TenantId.of(principal.tenantId().value()),
                order.noteVersionId(),
                ApprovalId.of(order.approvalId()),
                recipients,
                DeliveryPolicySnapshot.defaults(),
                subject,
                bodyText,
                principal.userId().toString()
        ));
        return EnqueueHttpView.from(result);
    }

    @GetMapping("/delivery/requests/{requestId}")
    @RequiresPermission(Permission.DELIVERY_MANAGE)
    public DeliveryRequestStatusView requestStatus(@PathVariable UUID requestId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.DELIVERY_MANAGE);
        DeliveryStatus status = deliveryApi.status(
                        principal.tenantId(),
                        DeliveryRequestId.of(requestId))
                .orElseThrow(() -> new ActenoraException(
                        "INTELLIGENCE_RESOURCE_NOT_FOUND",
                        "Delivery request not found: " + requestId
                ));
        return new DeliveryRequestStatusView(requestId, status);
    }

    @PostMapping("/delivery/requests/{requestId}/confirm-delivered")
    @RequiresPermission(Permission.DELIVERY_MANAGE)
    public DeliveryRequestStatusView confirmDelivered(@PathVariable UUID requestId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.DELIVERY_MANAGE);
        DeliveryStatus status = deliveryApi.confirmDelivered(
                principal.tenantId(),
                DeliveryRequestId.of(requestId)
        );
        return new DeliveryRequestStatusView(requestId, status);
    }

    @PostMapping("/delivery/drain")
    @RequiresPermission(Permission.DELIVERY_MANAGE)
    public DrainHttpView drain() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.DELIVERY_MANAGE);
        List<DeliveryStatus> outcomes = deliveryWorker.pollOnce();
        return new DrainHttpView(outcomes.size(), outcomes);
    }

    @ExceptionHandler({
            ExternalDeliveryBlockedException.class,
            DeliveryDomainException.class,
            ActenoraException.class
    })
    public ResponseEntity<ProblemDetail> handleDomain(RuntimeException ex) {
        String code = ex instanceof ActenoraException actenora ? actenora.code() : "DELIVERY_ERROR";
        HttpStatus status = switch (code) {
            case "EXTERNAL_DELIVERY_BLOCKED", "NOTE_NOT_APPROVED", "DELIVERY_ORDER_NOT_READY", "INVALID_STATUS" ->
                    HttpStatus.CONFLICT;
            case "TENANT_ISOLATION_VIOLATION" -> HttpStatus.FORBIDDEN;
            case "INTELLIGENCE_RESOURCE_NOT_FOUND", "DELIVERY_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(code);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }

    private DeliveryOrderView requireOrder(UUID tenantId, UUID orderId) {
        return deliveryApi.getOrder(tenantId, orderId)
                .orElseThrow(() -> new ActenoraException(
                        "INTELLIGENCE_RESOURCE_NOT_FOUND",
                        "Delivery order not found: " + orderId
                ));
    }

    private static List<DeliveryRecipient> mapRecipients(List<RecipientBody> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            throw new ActenoraException("INVALID_RECIPIENT", "at least one recipient is required");
        }
        List<DeliveryRecipient> recipients = new ArrayList<>(bodies.size());
        for (RecipientBody body : bodies) {
            if (body == null || body.email() == null || body.email().isBlank()) {
                throw new ActenoraException("INVALID_RECIPIENT", "recipient email is required");
            }
            RecipientKind kind = parseKind(body.kind());
            recipients.add(DeliveryRecipient.of(body.email(), kind, body.displayName()));
        }
        return List.copyOf(recipients);
    }

    private static RecipientKind parseKind(String kind) {
        if (kind == null || kind.isBlank() || "EXTERNAL".equalsIgnoreCase(kind.trim())) {
            return RecipientKind.EXTERNAL;
        }
        if ("INTERNAL".equalsIgnoreCase(kind.trim())) {
            return RecipientKind.INTERNAL;
        }
        throw new ActenoraException("INVALID_RECIPIENT", "Unsupported recipient kind: " + kind);
    }

    public record RequestDeliveryOrderBody(UUID approvalId, UUID noteVersionId, String channel) {
    }

    public record EnqueueDeliveryBody(
            List<RecipientBody> recipients,
            String subject,
            String bodyText
    ) {
    }

    public record RecipientBody(String email, String kind, String displayName) {
    }

    public record DeliveryOrderHttpView(
            UUID id,
            UUID approvalId,
            UUID noteVersionId,
            String channel,
            DeliveryOrderStatus status,
            Instant createdAt
    ) {
        static DeliveryOrderHttpView from(DeliveryOrderView order) {
            return new DeliveryOrderHttpView(
                    order.id(),
                    order.approvalId(),
                    order.noteVersionId(),
                    order.channel(),
                    order.status(),
                    order.createdAt()
            );
        }
    }

    public record EnqueueHttpView(List<UUID> createdIds, List<UUID> duplicateIds) {
        static EnqueueHttpView from(EnqueueDeliveryResult result) {
            return new EnqueueHttpView(
                    result.createdIds().stream().map(DeliveryRequestId::value).toList(),
                    result.duplicateIds().stream().map(DeliveryRequestId::value).toList()
            );
        }
    }

    public record DeliveryRequestStatusView(UUID id, DeliveryStatus status) {
    }

    public record DrainHttpView(int processed, List<DeliveryStatus> outcomes) {
    }
}
