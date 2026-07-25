/**
 * Bounded context: Model Management.
 * Public API package: com.nanobaseai.actenora.modelmanagement.api (named interface "api").
 * Internal packages are not accessible from other modules.
 * Tenant allowlist is consumed via {@code TenantModelAllowlistPort} (wired to Policy BC in platform).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Model Management",
    allowedDependencies = {
        "sharedkernel"
    }
)
package com.nanobaseai.actenora.modelmanagement;
