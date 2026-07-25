package com.nanobaseai.actenora.transcript.domain.normalization;

import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntry;
import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntryKind;
import com.nanobaseai.actenora.transcript.domain.dictionary.TenantDictionary;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves speaker display names against the tenant dictionary.
 * Ambiguous matches are reported but never auto-finalized.
 */
public final class SpeakerResolver {

    private SpeakerResolver() {
    }

    public static SpeakerResolution resolve(
            UUID originalSegmentId, String rawDisplayName, TenantDictionary dictionary) {
        if (rawDisplayName == null || rawDisplayName.isBlank()) {
            return SpeakerResolution.missing(originalSegmentId);
        }

        List<DictionaryEntry> matches = dictionary.findAllMatches(
                DictionaryEntryKind.SPEAKER, rawDisplayName);

        if (matches.isEmpty()) {
            return SpeakerResolution.unresolved(originalSegmentId, rawDisplayName);
        }
        if (matches.size() > 1) {
            List<UUID> ids = matches.stream().map(DictionaryEntry::id).collect(Collectors.toList());
            return SpeakerResolution.ambiguous(originalSegmentId, rawDisplayName, ids);
        }

        DictionaryEntry entry = matches.getFirst();
        if (entry.isExactCanonical(rawDisplayName)) {
            return SpeakerResolution.exact(
                    originalSegmentId, rawDisplayName, entry.id(), entry.canonical());
        }
        return SpeakerResolution.alias(
                originalSegmentId, rawDisplayName, entry.id(), entry.canonical());
    }
}
