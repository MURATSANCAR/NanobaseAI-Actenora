package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcMessagingSchemaTest {

    @Test
    void acceptsValidSchemaNames() {
        assertEquals("operations.outbox_event", JdbcMessagingSchema.table("operations", "outbox_event"));
    }

    @Test
    void rejectsInvalidSchemaNames() {
        assertThrows(IllegalArgumentException.class, () -> JdbcMessagingSchema.requireValid("Operations"));
        assertThrows(IllegalArgumentException.class, () -> JdbcMessagingSchema.requireValid("ops-schema"));
        assertThrows(IllegalArgumentException.class, () -> JdbcMessagingSchema.requireValid(""));
    }
}
