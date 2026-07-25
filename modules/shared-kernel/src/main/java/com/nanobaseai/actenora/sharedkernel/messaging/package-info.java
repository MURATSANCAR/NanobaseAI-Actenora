/**
 * Transactional outbox / inbox / DLQ event backbone (FAZ 10, ADR-004, ADR-008).
 * Broker adapters (RabbitMQ) plug into {@link com.nanobaseai.actenora.sharedkernel.messaging.port.EventTransport};
 * CDC relays can replace the polling publisher without changing stores.
 */
package com.nanobaseai.actenora.sharedkernel.messaging;
