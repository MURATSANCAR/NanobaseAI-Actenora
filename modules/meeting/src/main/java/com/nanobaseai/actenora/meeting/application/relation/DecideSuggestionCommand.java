package com.nanobaseai.actenora.meeting.application.relation;

import java.util.Objects;
import java.util.UUID;

public record DecideSuggestionCommand(
        UUID tenantId,
        UUID suggestionId,
        boolean approve,
        String actor
) {

    public DecideSuggestionCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(suggestionId, "suggestionId");
        Objects.requireNonNull(actor, "actor");
    }
}
