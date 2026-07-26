package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc;

import java.util.regex.Pattern;

/**
 * Validates and qualifies PostgreSQL schema names for messaging tables.
 */
public final class JdbcMessagingSchema {

    private static final Pattern VALID_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");

    private JdbcMessagingSchema() {
    }

    public static String requireValid(String schema) {
        if (schema == null || !VALID_IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException("Invalid schema identifier: " + schema);
        }
        return schema;
    }

    public static String table(String schema, String table) {
        requireValid(schema);
        requireValid(table);
        return schema + "." + table;
    }
}
