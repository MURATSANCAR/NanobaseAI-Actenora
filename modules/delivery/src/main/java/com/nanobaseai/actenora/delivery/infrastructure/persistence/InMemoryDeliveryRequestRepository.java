package com.nanobaseai.actenora.delivery.infrastructure.persistence;

import com.nanobaseai.actenora.delivery.application.port.DeliveryRequestRepository;
import com.nanobaseai.actenora.delivery.domain.DeliveryDeadLetter;
import com.nanobaseai.actenora.delivery.domain.DeliveryRequest;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryDeliveryRequestRepository implements DeliveryRequestRepository {

    private final Map<UUID, DeliveryRequest> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byIdempotency = new ConcurrentHashMap<>();
    private final Map<String, UUID> byProviderMessage = new ConcurrentHashMap<>();
    private final Map<UUID, DeliveryDeadLetter> deadLetters = new ConcurrentHashMap<>();

    @Override
    public DeliveryRequest save(DeliveryRequest request) {
        byId.put(request.id(), request);
        byIdempotency.put(
                idempotencyKey(request.tenantId(), request.noteVersionId(), request.recipient().email()),
                request.id()
        );
        for (var attempt : request.attempts()) {
            attempt.providerMessage().ifPresent(message -> byProviderMessage.put(
                    providerMessageKey(request.tenantId(), message.providerMessageId()),
                    request.id()
            ));
        }
        return request;
    }

    @Override
    public Optional<DeliveryRequest> findById(TenantId tenantId, UUID id) {
        return Optional.ofNullable(byId.get(id))
                .filter(r -> r.tenantId().equals(tenantId));
    }

    @Override
    public Optional<DeliveryRequest> findByNoteVersionAndRecipient(
            TenantId tenantId,
            UUID noteVersionId,
            String recipientEmail
    ) {
        UUID id = byIdempotency.get(idempotencyKey(tenantId, noteVersionId, recipientEmail));
        if (id == null) {
            return Optional.empty();
        }
        return findById(tenantId, id);
    }

    @Override
    public Optional<DeliveryRequest> findByProviderMessageId(TenantId tenantId, String providerMessageId) {
        if (providerMessageId == null || providerMessageId.isBlank()) {
            return Optional.empty();
        }
        UUID id = byProviderMessage.get(providerMessageKey(tenantId, providerMessageId));
        if (id == null) {
            return Optional.empty();
        }
        return findById(tenantId, id);
    }

    @Override
    public List<DeliveryRequest> findDue(Instant now, int limit) {
        return byId.values().stream()
                .filter(r -> r.isDue(now))
                .sorted(Comparator.comparing(DeliveryRequest::createdAt))
                .limit(limit)
                .toList();
    }

    @Override
    public void saveDeadLetter(DeliveryDeadLetter deadLetter) {
        deadLetters.put(deadLetter.id(), deadLetter);
    }

    @Override
    public Optional<DeliveryDeadLetter> findDeadLetter(UUID id) {
        return Optional.ofNullable(deadLetters.get(id));
    }

    @Override
    public List<DeliveryDeadLetter> listOpenDeadLetters(int limit) {
        return deadLetters.values().stream()
                .filter(d -> d.replayedAtOptional().isEmpty())
                .sorted(Comparator.comparing(DeliveryDeadLetter::deadLetteredAt))
                .limit(limit)
                .toList();
    }

    private static String idempotencyKey(TenantId tenantId, UUID noteVersionId, String email) {
        return tenantId.value() + "|" + noteVersionId + "|" + email.trim().toLowerCase();
    }

    private static String providerMessageKey(TenantId tenantId, String providerMessageId) {
        return tenantId.value() + "|" + providerMessageId.trim();
    }
}
