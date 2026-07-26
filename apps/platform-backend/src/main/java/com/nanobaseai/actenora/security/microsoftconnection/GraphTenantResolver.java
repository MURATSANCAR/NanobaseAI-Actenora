package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.tenant.api.TenantApi;
import com.nanobaseai.actenora.tenant.api.TenantView;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves Actenora {@link TenantId} from Graph notification tenant identifiers.
 * Graph may send either the Entra directory tid or the Actenora tenant UUID.
 */
public final class GraphTenantResolver {

    private GraphTenantResolver() {
    }

    public static Optional<TenantId> resolve(String rawTenantId, TenantApi tenantApi) {
        if (!StringUtils.hasText(rawTenantId)) {
            return Optional.empty();
        }
        String trimmed = rawTenantId.trim();
        try {
            UUID candidate = UUID.fromString(trimmed);
            Optional<TenantView> byId = tenantApi.findById(TenantId.of(candidate));
            if (byId.isPresent()) {
                return Optional.of(byId.get().id());
            }
            return tenantApi.findByEntraTenantId(trimmed).map(TenantView::id);
        } catch (IllegalArgumentException ex) {
            return tenantApi.findByEntraTenantId(trimmed).map(TenantView::id);
        }
    }

    public static Optional<UUID> resolveUuid(String rawTenantId, TenantApi tenantApi) {
        return resolve(rawTenantId, tenantApi).map(TenantId::value);
    }
}
