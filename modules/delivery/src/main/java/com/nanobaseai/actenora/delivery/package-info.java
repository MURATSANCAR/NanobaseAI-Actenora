/**
 * Bounded context: Delivery — approved-note mail dispatch (FAZ 20).
 * Public API package: com.nanobaseai.actenora.delivery.api (named interface "api").
 * Worker loop ({@code DeliveryWorker}) is extractable to {@code services/delivery-worker}.
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Delivery",
    allowedDependencies = {"sharedkernel","tenant :: api","approval :: api","template :: api"}
)
package com.nanobaseai.actenora.delivery;
