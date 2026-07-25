package com.nanobaseai.actenora.delivery.domain;

/**
 * Internal (tenant) vs external recipients. Externals always receive isolated messages.
 */
public enum RecipientKind {
    INTERNAL,
    EXTERNAL
}
