/**
 * Bounded context: Audit.
 * Public API package: com.nanobaseai.actenora.audit.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Audit"
    allowedDependencies = {
        "sharedkernel"
    }
)
package com.nanobaseai.actenora.audit;
