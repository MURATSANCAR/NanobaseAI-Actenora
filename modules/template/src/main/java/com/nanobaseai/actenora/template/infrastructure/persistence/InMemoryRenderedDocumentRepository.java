package com.nanobaseai.actenora.template.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.api.RenderedDocumentId;
import com.nanobaseai.actenora.template.application.port.out.RenderedDocumentRepository;
import com.nanobaseai.actenora.template.domain.RenderedDocument;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRenderedDocumentRepository implements RenderedDocumentRepository {

    private final Map<String, RenderedDocument> byId = new ConcurrentHashMap<>();
    private final Map<String, String> byJob = new ConcurrentHashMap<>();

    @Override
    public void save(RenderedDocument document) {
        byId.put(key(document.tenantId(), document.id()), document);
        byJob.put(jobKey(document.tenantId(), document.renderJobId()), key(document.tenantId(), document.id()));
    }

    @Override
    public Optional<RenderedDocument> findById(TenantId tenantId, RenderedDocumentId id) {
        return Optional.ofNullable(byId.get(key(tenantId, id)));
    }

    @Override
    public Optional<RenderedDocument> findByJobId(TenantId tenantId, RenderJobId jobId) {
        String idKey = byJob.get(jobKey(tenantId, jobId));
        if (idKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(idKey));
    }

    private static String key(TenantId tenantId, RenderedDocumentId id) {
        return tenantId.value() + ":" + id.value();
    }

    private static String jobKey(TenantId tenantId, RenderJobId jobId) {
        return tenantId.value() + ":" + jobId.value();
    }
}
