package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.util.Objects;

public record OutlookDraftResult(
        String providerMessageId,
        String webLink,
        boolean reused
) {
    public OutlookDraftResult {
        Objects.requireNonNull(providerMessageId, "providerMessageId");
    }

    public OutlookDraftResult asReused() {
        return reused ? this : new OutlookDraftResult(providerMessageId, webLink, true);
    }
}
