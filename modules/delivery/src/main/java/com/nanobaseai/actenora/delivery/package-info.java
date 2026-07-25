/**
 * Bounded context: Delivery.
 * Public API package: com.nanobaseai.actenora.delivery.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Delivery"
    allowedDependencies = {
        "sharedkernel",
        "tenant :: api",
        "approval :: api",
        "template :: api"
    }
)
package com.nanobaseai.actenora.delivery;
