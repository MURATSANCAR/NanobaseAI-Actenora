/**
 * Bounded context: Microsoft Connection.
 * Public API package: com.nanobaseai.actenora.microsoftconnection.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Microsoft Connection"
    allowedDependencies = {
        "sharedkernel",
        "tenant :: api"
    }
)
package com.nanobaseai.actenora.microsoftconnection;
