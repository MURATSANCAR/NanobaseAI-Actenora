package com.nanobaseai.actenora.template.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.MeetingTemplateId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.application.port.out.MeetingTemplateRepository;
import com.nanobaseai.actenora.template.domain.MeetingTemplate;
import com.nanobaseai.actenora.template.domain.TemplateVersion;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMeetingTemplateRepository implements MeetingTemplateRepository {

    private final Map<String, MeetingTemplate> byId = new ConcurrentHashMap<>();
    private final Map<String, TemplateVersionId> versionIndex = new ConcurrentHashMap<>();

    @Override
    public void save(MeetingTemplate template) {
        byId.put(key(template.tenantId(), template.id()), template);
        for (TemplateVersion version : template.versions()) {
            versionIndex.put(versionKey(template.tenantId(), version.id()), version.id());
        }
    }

    @Override
    public Optional<MeetingTemplate> findById(TenantId tenantId, MeetingTemplateId id) {
        return Optional.ofNullable(byId.get(key(tenantId, id)));
    }

    @Override
    public List<MeetingTemplate> listByTenant(TenantId tenantId) {
        return byId.values().stream()
                .filter(t -> t.tenantId().equals(tenantId))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    @Override
    public Optional<TemplateVersion> findVersion(TenantId tenantId, TemplateVersionId versionId) {
        for (MeetingTemplate template : byId.values()) {
            if (!template.tenantId().equals(tenantId)) {
                continue;
            }
            for (TemplateVersion version : template.versions()) {
                if (version.id().equals(versionId)) {
                    return Optional.of(version);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void saveVersion(MeetingTemplate template, TemplateVersion version) {
        save(template);
    }

    private static String key(TenantId tenantId, MeetingTemplateId id) {
        return tenantId.value() + ":" + id.value();
    }

    private static String versionKey(TenantId tenantId, TemplateVersionId id) {
        return tenantId.value() + ":" + id.value();
    }
}
