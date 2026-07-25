package com.nanobaseai.actenora.meeting.application;

import com.nanobaseai.actenora.meeting.api.dto.BusinessContextResponse;
import com.nanobaseai.actenora.meeting.api.dto.CreateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.api.dto.UpdateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.application.port.BusinessContextRepository;
import com.nanobaseai.actenora.meeting.application.port.ClockPort;
import com.nanobaseai.actenora.meeting.application.port.MeetingAuditPort;
import com.nanobaseai.actenora.meeting.application.port.TenantContextPort;
import com.nanobaseai.actenora.meeting.domain.exception.BusinessContextNotFoundException;
import com.nanobaseai.actenora.meeting.domain.exception.DuplicateBusinessContextException;
import com.nanobaseai.actenora.meeting.domain.model.BusinessContext;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BusinessContextApplicationService {

    private final TenantContextPort tenantContext;
    private final BusinessContextRepository repository;
    private final MeetingAuditPort auditPort;
    private final ClockPort clock;

    public BusinessContextApplicationService(
            TenantContextPort tenantContext,
            BusinessContextRepository repository,
            MeetingAuditPort auditPort,
            ClockPort clock
    ) {
        this.tenantContext = Objects.requireNonNull(tenantContext);
        this.repository = Objects.requireNonNull(repository);
        this.auditPort = Objects.requireNonNull(auditPort);
        this.clock = Objects.requireNonNull(clock);
    }

    public BusinessContextResponse create(CreateBusinessContextRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        Objects.requireNonNull(request, "request");
        assertUniqueReferenceCode(tenantId, request.referenceCode(), null);
        BusinessContext created = BusinessContext.create(
                tenantId,
                request.type(),
                request.referenceCode(),
                request.name(),
                request.description(),
                clock.now()
        );
        BusinessContext saved = repository.save(created);
        auditPort.record(tenantId, actor, "BUSINESS_CONTEXT_CREATED", "BusinessContext", saved.id(),
                Map.of("name", saved.name(), "type", saved.type()));
        return MeetingMapper.toResponse(saved);
    }

    public List<BusinessContextResponse> list() {
        TenantId tenantId = tenantContext.requireTenantId();
        return repository.listByTenantId(tenantId).stream().map(MeetingMapper::toResponse).toList();
    }

    public BusinessContextResponse get(UUID id) {
        TenantId tenantId = tenantContext.requireTenantId();
        BusinessContext existing = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessContextNotFoundException(id));
        return MeetingMapper.toResponse(existing);
    }

    public BusinessContextResponse update(UUID id, UpdateBusinessContextRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        BusinessContext existing = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessContextNotFoundException(id));
        if (request.referenceCode() != null
                && !request.referenceCode().equalsIgnoreCase(existing.referenceCode())) {
            assertUniqueReferenceCode(tenantId, request.referenceCode(), existing.id());
        }
        existing.update(
                request.type(),
                request.referenceCode(),
                request.name(),
                request.description(),
                request.status(),
                request.expectedVersion(),
                clock.now()
        );
        BusinessContext saved = repository.save(existing);
        auditPort.record(tenantId, actor, "BUSINESS_CONTEXT_UPDATED", "BusinessContext", saved.id(),
                Map.of("version", saved.version()));
        return MeetingMapper.toResponse(saved);
    }

    private void assertUniqueReferenceCode(TenantId tenantId, String referenceCode, UUID excludingId) {
        repository.findByTenantIdAndReferenceCode(tenantId, referenceCode).ifPresent(existing -> {
            if (excludingId == null || !existing.id().equals(excludingId)) {
                throw new DuplicateBusinessContextException(referenceCode);
            }
        });
    }
}
