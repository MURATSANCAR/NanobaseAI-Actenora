/**
 * Bounded context: Tenant.
 * Public API package: com.nanobaseai.actenora.tenant.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Tenant",
    allowedDependencies = {"sharedkernel","identity :: api"}
)
package com.nanobaseai.actenora.tenant;
