package com.nanobaseai.actenora.transcript.domain.dictionary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical term plus aliases used for deterministic text / speaker rewrites.
 */
public final class DictionaryEntry {

    private final UUID id;
    private final DictionaryEntryKind kind;
    private final String canonical;
    private final List<String> aliases;
    private final String externalRef;
    private final boolean active;

    public DictionaryEntry(
            UUID id,
            DictionaryEntryKind kind,
            String canonical,
            List<String> aliases,
            String externalRef,
            boolean active) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.canonical = requireNonBlank(canonical, "canonical");
        this.aliases = List.copyOf(aliases == null ? List.of() : aliases);
        this.externalRef = externalRef;
        this.active = active;
    }

    public static DictionaryEntry of(
            DictionaryEntryKind kind, String canonical, List<String> aliases) {
        return new DictionaryEntry(UUID.randomUUID(), kind, canonical, aliases, null, true);
    }

    public boolean matches(String raw) {
        if (!active || raw == null || raw.isBlank()) {
            return false;
        }
        String needle = fold(raw);
        if (needle.equals(fold(canonical))) {
            return true;
        }
        for (String alias : aliases) {
            if (needle.equals(fold(alias))) {
                return true;
            }
        }
        return false;
    }

    public boolean isExactCanonical(String raw) {
        return active && raw != null && fold(raw).equals(fold(canonical));
    }

    public boolean isAliasMatch(String raw) {
        if (!active || raw == null || raw.isBlank()) {
            return false;
        }
        String needle = fold(raw);
        if (needle.equals(fold(canonical))) {
            return false;
        }
        for (String alias : aliases) {
            if (needle.equals(fold(alias))) {
                return true;
            }
        }
        return false;
    }

    static String fold(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.forLanguageTag("tr"));
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public UUID id() {
        return id;
    }

    public DictionaryEntryKind kind() {
        return kind;
    }

    public String canonical() {
        return canonical;
    }

    public List<String> aliases() {
        return aliases;
    }

    public String externalRef() {
        return externalRef;
    }

    public boolean active() {
        return active;
    }

    public List<String> allSurfaceForms() {
        List<String> forms = new ArrayList<>();
        forms.add(canonical);
        forms.addAll(aliases);
        return List.copyOf(forms);
    }
}
