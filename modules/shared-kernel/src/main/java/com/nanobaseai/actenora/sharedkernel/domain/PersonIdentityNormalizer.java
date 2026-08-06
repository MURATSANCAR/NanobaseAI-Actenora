package com.nanobaseai.actenora.sharedkernel.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared, deterministic person-name canonicalization for calendar, Teams, transcript and AI data.
 */
public final class PersonIdentityNormalizer {

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9.-]+$");
    private static final Pattern ADDRESS_WITH_NAME = Pattern.compile(
            "^\\s*(.*?)\\s*<([^<>]+@[^<>]+)>\\s*$");
    private static final Pattern TRAILING_CONTEXT = Pattern.compile("\\s*\\([^()]*\\)\\s*$");
    private static final Pattern GENERIC_SPEAKER = Pattern.compile(
            "(?iu)^(?:speaker|konu[sş]mac[ıi]|unknown|bilinmeyen)(?:\\s+\\d+)?$");

    private PersonIdentityNormalizer() {
    }

    public static String displayName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.strip().replaceAll("\\s+", " ");
        Matcher addressed = ADDRESS_WITH_NAME.matcher(value);
        if (addressed.matches()) {
            String name = addressed.group(1).strip();
            value = name.isBlank() ? addressed.group(2).strip() : name;
        }
        String previous;
        do {
            previous = value;
            value = TRAILING_CONTEXT.matcher(value).replaceFirst("").strip();
        } while (!value.equals(previous));
        int titleSeparator = value.indexOf(" - ");
        if (titleSeparator > 0) {
            value = value.substring(0, titleSeparator).strip();
        }
        if (EMAIL.matcher(value).matches()) {
            return titleCaseEmailLocalPart(value.substring(0, value.indexOf('@')));
        }
        return value.replaceAll("\\s+", " ").strip();
    }

    public static String identityKey(String raw) {
        String display = displayName(raw);
        if (display.isBlank()) {
            return "";
        }
        String folded = display
                .replace('ı', 'i')
                .replace('İ', 'I');
        folded = Normalizer.normalize(folded, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{Alnum}]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return folded
                .replaceAll("\\b(?:bey|hanim|bay|bayan|mr|mrs|ms|dr|prof)\\b", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    public static boolean isGenericSpeakerLabel(String raw) {
        return raw == null || raw.isBlank() || GENERIC_SPEAKER.matcher(raw.strip()).matches();
    }

    /**
     * Soft person match for dedupe / alias compare: exact identity key, or unique
     * short↔full first-token equivalence (e.g. {@code Murat} ≈ {@code Murat Sancar}).
     */
    public static boolean softMatch(String left, String right) {
        String leftKey = identityKey(left);
        String rightKey = identityKey(right);
        if (leftKey.isBlank() || rightKey.isBlank()) {
            return false;
        }
        if (leftKey.equals(rightKey)) {
            return true;
        }
        boolean shortForm = tokenCount(leftKey) == 1 || tokenCount(rightKey) == 1;
        return shortForm && tokenEquivalent(firstToken(leftKey), firstToken(rightKey));
    }

    public static Optional<String> resolveUnique(String candidate, Collection<String> roster) {
        String candidateKey = identityKey(candidate);
        if (candidateKey.isBlank() || roster == null || roster.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> identities = canonicalRoster(roster);
        String exact = identities.get(candidateKey);
        if (exact != null) {
            return Optional.of(exact);
        }

        String candidateFirst = firstToken(candidateKey);
        Map<String, String> matches = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : identities.entrySet()) {
            String rosterFirst = firstToken(entry.getKey());
            boolean shortForm = tokenCount(candidateKey) == 1 || tokenCount(entry.getKey()) == 1;
            if (shortForm && tokenEquivalent(candidateFirst, rosterFirst)) {
                matches.put(entry.getKey(), entry.getValue());
            }
        }
        return matches.size() == 1
                ? Optional.of(matches.values().iterator().next())
                : Optional.empty();
    }

    public static Map<String, String> canonicalRoster(Collection<String> names) {
        Map<String, String> byIdentity = new LinkedHashMap<>();
        if (names == null) {
            return byIdentity;
        }
        for (String raw : names) {
            if (isGenericSpeakerLabel(raw)) {
                continue;
            }
            String display = displayName(raw);
            if (isGenericSpeakerLabel(display)) {
                continue;
            }
            String key = identityKey(display);
            if (key.isBlank()) {
                continue;
            }
            byIdentity.merge(key, display, PersonIdentityNormalizer::preferDisplayName);
        }
        mergeUniqueShortAliases(byIdentity);
        return byIdentity;
    }

    private static void mergeUniqueShortAliases(Map<String, String> identities) {
        List<String> shortKeys = new ArrayList<>(identities.keySet().stream()
                .filter(key -> tokenCount(key) == 1)
                .toList());
        for (String shortKey : shortKeys) {
            if (!identities.containsKey(shortKey)) {
                continue;
            }
            List<String> fullMatches = identities.keySet().stream()
                    .filter(key -> tokenCount(key) > 1)
                    .filter(key -> tokenEquivalent(shortKey, firstToken(key)))
                    .toList();
            if (fullMatches.size() != 1) {
                continue;
            }
            String shortDisplay = identities.remove(shortKey);
            String fullKey = fullMatches.getFirst();
            identities.merge(fullKey, shortDisplay, PersonIdentityNormalizer::preferDisplayName);
        }
    }

    private static String preferDisplayName(String left, String right) {
        int leftScore = displayQuality(left);
        int rightScore = displayQuality(right);
        return rightScore > leftScore ? right : left;
    }

    private static int displayQuality(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        int tokens = tokenCount(identityKey(value));
        int letters = value.replaceAll("[^\\p{L}]", "").length();
        return tokens * 100 + letters;
    }

    private static boolean tokenEquivalent(String left, String right) {
        if (left.equals(right)) {
            return true;
        }
        return left.length() >= 4 && right.length() >= 4 && editDistanceAtMostOne(left, right);
    }

    private static boolean editDistanceAtMostOne(String left, String right) {
        if (Math.abs(left.length() - right.length()) > 1) {
            return false;
        }
        int i = 0;
        int j = 0;
        int edits = 0;
        while (i < left.length() && j < right.length()) {
            if (left.charAt(i) == right.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (++edits > 1) {
                return false;
            }
            if (left.length() > right.length()) {
                i++;
            } else if (right.length() > left.length()) {
                j++;
            } else {
                i++;
                j++;
            }
        }
        return edits + (i < left.length() || j < right.length() ? 1 : 0) <= 1;
    }

    private static String titleCaseEmailLocalPart(String local) {
        String[] tokens = local.replaceAll("[._+\\-]+", " ").strip().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                out.append(token.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.toString();
    }

    private static String firstToken(String value) {
        int split = value.indexOf(' ');
        return split < 0 ? value : value.substring(0, split);
    }

    private static int tokenCount(String value) {
        return value == null || value.isBlank() ? 0 : value.split("\\s+").length;
    }
}
