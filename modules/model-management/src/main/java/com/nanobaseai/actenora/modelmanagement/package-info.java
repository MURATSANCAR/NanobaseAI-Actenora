/**
 * Bounded context: Model Management.
 * Public API package: com.nanobaseai.actenora.modelmanagement.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Model Management"
    allowedDependencies = {
        "sharedkernel",
        "tenant :: api",
        "policy :: api"
    }
)
package com.nanobaseai.actenora.modelmanagement;
