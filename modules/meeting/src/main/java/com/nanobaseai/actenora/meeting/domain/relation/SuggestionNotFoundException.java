package com.nanobaseai.actenora.meeting.domain.relation;

import java.util.UUID;

public final class SuggestionNotFoundException extends RuntimeException {

    private final UUID suggestionId;

    public SuggestionNotFoundException(UUID suggestionId) {
        super("Meeting relation suggestion not found: " + suggestionId);
        this.suggestionId = suggestionId;
    }

    public UUID suggestionId() {
        return suggestionId;
    }

    public String code() {
        return "SUGGESTION_NOT_FOUND";
    }
}
