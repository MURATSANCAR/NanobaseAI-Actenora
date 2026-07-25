/**
 * Bounded context: Operations.
 * Public API package: com.nanobaseai.actenora.operations.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Operations"
    allowedDependencies = {
        "sharedkernel",
        "tenant :: api"
    }
)
package com.nanobaseai.actenora.operations;
