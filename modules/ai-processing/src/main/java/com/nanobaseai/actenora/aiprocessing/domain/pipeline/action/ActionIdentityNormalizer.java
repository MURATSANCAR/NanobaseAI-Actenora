package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared canonical identity logic for action dedup, audit, and safe traces.
 */
public final class ActionIdentityNormalizer {

    private static final Pattern EXPLICIT_OWNER = Pattern.compile(
            "(?iu)^\\s*([\\p{L}][\\p{L}'\\-]{1,40})(?:\\s*,\\s*|\\s+)(.+)$"
    );
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{Alnum}]+");
    private static final Set<String> AUXILIARY_STEMS = Set.of(
            "gerceklestir", "gercekleştir", "yap", "ol", "et", "tamamla", "bitir"
    );

    public String canonicalOwner(ActionItemCandidate action) {
        if (action == null) {
            return "";
        }
        String explicit = normalizeLoose(action.owner());
        if (!explicit.isBlank()) {
            return explicit;
        }
        return ownerHintFromText(action.text());
    }

    public String ownerHintFromText(String text) {
        String raw = text == null ? "" : text.strip();
        Matcher matcher = EXPLICIT_OWNER.matcher(raw);
        if (!matcher.matches()) {
            return "";
        }
        String candidate = normalizeLoose(matcher.group(1));
        return candidate.length() < 2 ? "" : candidate;
    }

    public String canonicalCore(ActionItemCandidate action) {
        if (action == null) {
            return "";
        }
        String stripped = new ActionDiscoursePrefixNormalizer().strip(action.text());
        String normalized = normalizeLoose(stripped);
        String owner = normalizeLoose(action.owner());
        if (!owner.isBlank()) {
            normalized = normalized.replaceFirst("(?iu)^" + Pattern.quote(owner) + "\\s+", "");
        } else {
            Matcher matcher = EXPLICIT_OWNER.matcher(normalized);
            if (matcher.matches() && stripped.contains(",")) {
                normalized = matcher.group(2);
            }
        }
        return canonicalCoreFromNormalized(normalized);
    }

    public String canonicalCore(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String stripped = new ActionDiscoursePrefixNormalizer().strip(text);
        String normalized = normalizeLoose(stripped);
        return canonicalCoreFromNormalized(normalized);
    }

    private String canonicalCoreFromNormalized(String normalized) {
        normalized = normalized
                .replace("correlation id", "correlationid")
                .replace("correlation ıd", "correlationid")
                .replace("utf 8", "utf8");

        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            String stem = normalizeToken(token);
            if (stem.isBlank() || AUXILIARY_STEMS.contains(stem)) {
                continue;
            }
            tokens.add(stem);
        }
        return String.join(" ", tokens).strip();
    }

    public String identityKey(ActionItemCandidate action) {
        if (action == null) {
            return "";
        }
        return canonicalOwner(action) + "|" + canonicalCore(action);
    }

    public String textHash(String text) {
        return sha256(text == null ? "" : text);
    }

    public String coreHash(ActionItemCandidate action) {
        return sha256(canonicalCore(action));
    }

    public String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public Set<String> tokenSet(String core) {
        Set<String> out = new LinkedHashSet<>();
        if (core == null || core.isBlank()) {
            return out;
        }
        for (String token : core.split("\\s+")) {
            if (token.length() >= 2) {
                out.add(token);
            }
        }
        return out;
    }

    public String normalizeLoose(String value) {
        if (value == null) {
            return "";
        }
        String text = value
                .strip()
                .toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replace('İ', 'i')
                .replace('ş', 's')
                .replace('Ş', 's')
                .replace('ğ', 'g')
                .replace('Ğ', 'g')
                .replace('ç', 'c')
                .replace('Ç', 'c')
                .replace('ö', 'o')
                .replace('Ö', 'o')
                .replace('ü', 'u')
                .replace('Ü', 'u');
        text = NON_ALNUM.matcher(text).replaceAll(" ");
        return text.replaceAll("\\s+", " ").strip();
    }

    private static String normalizeToken(String token) {
        if (token == null) {
            return "";
        }
        String t = token.strip();
        if (t.isBlank()) {
            return "";
        }
        if (t.endsWith("mesini") || t.endsWith("masini")) {
            t = t.substring(0, t.length() - 6);
        } else if (t.endsWith("mesini") || t.endsWith("masını")) {
            t = t.substring(0, t.length() - 6);
        } else if (t.endsWith("yecek") || t.endsWith("yacak")) {
            t = t.substring(0, t.length() - 5);
        } else if (t.endsWith("ecek") || t.endsWith("acak")) {
            t = t.substring(0, t.length() - 4);
        } else if (t.endsWith("iyor")) {
            t = t.substring(0, t.length() - 4);
        }
        if (t.endsWith("mesini") || t.endsWith("masını")) {
            t = t.substring(0, t.length() - 6);
        }
        if (t.endsWith("sini") || t.endsWith("sını")) {
            t = t.substring(0, t.length() - 4);
        }
        if (t.endsWith("si") || t.endsWith("sı")) {
            t = t.substring(0, t.length() - 2);
        }
        return t.strip();
    }
}
