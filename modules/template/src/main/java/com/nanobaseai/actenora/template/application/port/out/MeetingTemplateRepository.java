package com.nanobaseai.actenora.template.application.port.out;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.MeetingTemplateId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.domain.MeetingTemplate;
import com.nanobaseai.actenora.template.domain.TemplateVersion;

import java.util.List;
import java.util.Optional;

public interface MeetingTemplateRepository {

    void save(MeetingTemplate template);

    Optional<MeetingTemplate> findById(TenantId tenantId, MeetingTemplateId id);

    List<MeetingTemplate> listByTenant(TenantId tenantId);

    Optional<TemplateVersion> findVersion(TenantId tenantId, TemplateVersionId versionId);

    void saveVersion(MeetingTemplate template, TemplateVersion version);
}
