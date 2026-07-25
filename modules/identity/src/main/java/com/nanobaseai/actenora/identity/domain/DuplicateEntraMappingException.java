package com.nanobaseai.actenora.identity.domain;

public final class DuplicateEntraMappingException extends RuntimeException {

    private final String entraObjectId;

    public DuplicateEntraMappingException(String entraObjectId) {
        super("Entra object already mapped: " + entraObjectId);
        this.entraObjectId = entraObjectId;
    }

    public String entraObjectId() {
        return entraObjectId;
    }
}
