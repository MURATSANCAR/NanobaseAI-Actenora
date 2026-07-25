package com.nanobaseai.actenora.transcript.api.dto;

import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntryKind;

import java.util.List;

public record AddDictionaryEntryRequest(
        DictionaryEntryKind kind,
        String canonical,
        List<String> aliases,
        String externalRef
) {
}
