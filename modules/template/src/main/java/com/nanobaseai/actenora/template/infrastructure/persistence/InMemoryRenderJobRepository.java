package com.nanobaseai.actenora.template.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.application.port.out.RenderJobRepository;
import com.nanobaseai.actenora.template.domain.ContentHash;
import com.nanobaseai.actenora.template.domain.RenderJob;
import com.nanobaseai.actenora.template.domain.RenderJobStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRenderJobRepository implements RenderJobRepository {

    private final Map<String, RenderJob> byId = new ConcurrentHashMap<>();
    private final Map<String, String> byHash = new ConcurrentHashMap<>();

    @Override
    public void save(RenderJob job) {
        byId.put(key(job.tenantId(), job.id()), job);
        byHash.put(hashKey(job.tenantId(), job.contentHash()), key(job.tenantId(), job.id()));
    }

    @Override
    public Optional<RenderJob> findById(TenantId tenantId, RenderJobId id) {
        return Optional.ofNullable(byId.get(key(tenantId, id)));
    }

    @Override
    public Optional<RenderJob> findByContentHash(TenantId tenantId, ContentHash contentHash) {
        String idKey = byHash.get(hashKey(tenantId, contentHash));
        if (idKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(idKey));
    }

    @Override
    public List<RenderJob> findPending(int limit) {
        List<RenderJob> pending = new ArrayList<>();
        for (RenderJob job : byId.values()) {
            if (job.status() == RenderJobStatus.PENDING) {
                pending.add(job);
            }
        }
        pending.sort(Comparator.comparing(RenderJob::createdAt));
        if (pending.size() > limit) {
            return pending.subList(0, limit);
        }
        return pending;
    }

    private static String key(TenantId tenantId, RenderJobId id) {
        return tenantId.value() + ":" + id.value();
    }

    private static String hashKey(TenantId tenantId, ContentHash hash) {
        return tenantId.value() + ":" + hash.sha256Hex();
    }
}
