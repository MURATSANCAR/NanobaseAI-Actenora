package com.nanobaseai.actenora.template.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;

import java.util.Objects;

/**
 * Tenant-scoped object keys for templates and rendered documents.
 * Pattern: tenants/{tenantId}/templates/... and tenants/{tenantId}/renders/...
 */
public final class TemplateObjectKeys {

    private TemplateObjectKeys() {
    }

    public static String renderedDocument(
            TenantId tenantId,
            TemplateVersionId versionId,
            RenderJobId jobId,
            RenderFormat format) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(format, "format");
        return "tenants/" + tenantId.value()
                + "/renders/" + versionId.value()
                + "/" + jobId.value()
                + "/document." + format.fileExtension();
    }

    public static void assertTenantOwnsKey(TenantId tenantId, String key) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(key, "key");
        String prefix = "tenants/" + tenantId.value() + "/";
        if (!key.startsWith(prefix)) {
            throw new TemplateDomainException("TENANT_KEY_MISMATCH", "Object key does not belong to tenant");
        }
    }
}
