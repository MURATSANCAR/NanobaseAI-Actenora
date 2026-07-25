package com.nanobaseai.actenora.transcript.domain.normalization;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Deterministic whitespace + Unicode normalization for transcript text.
 */
public final class WhitespaceNormalizer {

    private static final Pattern MULTI_WHITESPACE = Pattern.compile("[\\p{Z}\\s]+");

    private WhitespaceNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String nfc = Normalizer.normalize(input, Normalizer.Form.NFC);
        String collapsed = MULTI_WHITESPACE.matcher(nfc).replaceAll(" ");
        return collapsed.trim();
    }
}
