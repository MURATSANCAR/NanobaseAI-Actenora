/**
 * Bounded context: Identity.
 * Public API package: com.nanobaseai.actenora.identity.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Identity",
    allowedDependencies = {"sharedkernel"}
)
package com.nanobaseai.actenora.identity;
