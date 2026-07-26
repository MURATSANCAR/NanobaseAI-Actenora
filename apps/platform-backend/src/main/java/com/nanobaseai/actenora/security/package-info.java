/**
 * Application composition root: HTTP adapters, security filters, and InMemory/JDBC
 * platform wiring. Not a product bounded context — may depend on BC internals until
 * adapters are relocated into modules (post Wave 0).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "SecurityHost",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package com.nanobaseai.actenora.security;
