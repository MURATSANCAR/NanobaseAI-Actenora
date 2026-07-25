/**
 * Bounded context: Approval.
 * Public API package: com.nanobaseai.actenora.approval.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Approval"
    allowedDependencies = {
        "sharedkernel",
        "tenant :: api",
        "policy :: api"
    }
)
package com.nanobaseai.actenora.approval;
