package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic semantic core for multi-signal decision/proposal matching.
 */
public final class SemanticCore {

    public enum Polarity {
        AFFIRMATIVE,
        NEGATIVE,
        UNKNOWN
    }

    private static final Pattern NEGATIVE = Pattern.compile(
            "(?iu)\\b(de[gğ]i[sş]tir(me(yece[gğ]iz|miyoruz|meyecek)|ilmeyecek)|yapmıyoruz|yok|"
                    + "kalacak|ayn[ıi]\\s+kal|zorunlu\\s+de[gğ]il|karar\\s+de[gğ]il)\\b"
    );
    private static final Pattern AFFIRMATIVE_ACTION = Pattern.compile(
            "(?iu)\\b(birle[sş]tir\\w*|topla\\w*|zorunlu\\w*|yap[ıi]lacak|eklenecek|yönlendir\\w*|yonlendir\\w*|"
                    + "al[ıi]nacak|sabitle\\w*|dondur\\w*|uygula\\w*|ge[cç]ici|read\\s+replica|promise)\\b"
    );
    private static final Pattern CONSOLIDATE = Pattern.compile(
            "(?iu)\\b(birle[sş]tir\\w*|tek\\s+promise|ortak\\s+(bir\\s+)?i[sş]lem|single\\s+promise|consolidate|toplan\\w*)\\b"
    );
    private static final Pattern KEEP = Pattern.compile(
            "(?iu)\\b(de[gğ]i[sş]tir(me|ilmeyecek)|ayn[ıi]\\s+kal|pool\\s+değer[iı]\\s+ayn[ıi])\\b"
    );
    private static final Pattern FRONTEND = Pattern.compile("(?iu)\\b(frontend|istemci|client|web)\\b");
    private static final Pattern MOBILE = Pattern.compile("(?iu)\\b(mobil|mobile|ios|android)\\b");
    private static final Pattern TOKEN_REFRESH = Pattern.compile(
            "(?iu)\\b(token|refresh|yenileme|paralel\\s+[cç]a[gğ]r)\\w*\\b"
    );
    // Do not key on "başlık" alone — acceptance-criteria proposals also say "başlık".
    private static final Pattern UTF8 = Pattern.compile(
            "(?iu)\\b(utf8|utf-?8|encoding|quotedprintable|quoted[\\s-]?printable)\\b"
    );

    private final String subjectKey;
    private final String actionKey;
    private final String scopeKey;
    private final Polarity polarity;
    private final Set<String> tokens;

    public SemanticCore(String subjectKey, String actionKey, String scopeKey, Polarity polarity, Set<String> tokens) {
        this.subjectKey = subjectKey == null ? "" : subjectKey;
        this.actionKey = actionKey == null ? "" : actionKey;
        this.scopeKey = scopeKey == null ? "" : scopeKey;
        this.polarity = polarity == null ? Polarity.UNKNOWN : polarity;
        this.tokens = Set.copyOf(tokens == null ? Set.of() : tokens);
    }

    public static SemanticCore extract(String comparisonCore) {
        String core = comparisonCore == null ? "" : comparisonCore;
        String subject = "other";
        if (TOKEN_REFRESH.matcher(core).find()) {
            subject = "token_refresh";
        } else if (UTF8.matcher(core).find()) {
            subject = "email_encoding";
        }
        String action = "other";
        if (CONSOLIDATE.matcher(core).find()) {
            action = "consolidate";
        } else if (KEEP.matcher(core).find()) {
            action = "keep";
        } else if (AFFIRMATIVE_ACTION.matcher(core).find()) {
            action = "apply";
        }
        String scope = "general";
        if (MOBILE.matcher(core).find()) {
            scope = "mobile";
        } else if (FRONTEND.matcher(core).find()) {
            scope = "web";
        }
        Polarity polarity = Polarity.UNKNOWN;
        if (NEGATIVE.matcher(core).find() || "keep".equals(action)) {
            polarity = Polarity.NEGATIVE;
        } else if (AFFIRMATIVE_ACTION.matcher(core).find() || "consolidate".equals(action) || "apply".equals(action)) {
            polarity = Polarity.AFFIRMATIVE;
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String t : core.split("\\s+")) {
            if (t.length() >= 3) {
                tokens.add(t.toLowerCase(Locale.ROOT));
            }
        }
        return new SemanticCore(subject, action, scope, polarity, tokens);
    }

    public String subjectKey() {
        return subjectKey;
    }

    public String actionKey() {
        return actionKey;
    }

    public String scopeKey() {
        return scopeKey;
    }

    public Polarity polarity() {
        return polarity;
    }

    public Set<String> tokens() {
        return tokens;
    }

    public double topicSimilarity(SemanticCore other) {
        Objects.requireNonNull(other, "other");
        if (!subjectKey.isBlank() && subjectKey.equals(other.subjectKey) && !"other".equals(subjectKey)) {
            return 1.0d;
        }
        return jaccard(tokens, other.tokens);
    }

    public double actionSimilarity(SemanticCore other) {
        Objects.requireNonNull(other, "other");
        if (!actionKey.isBlank() && actionKey.equals(other.actionKey) && !"other".equals(actionKey)) {
            return 1.0d;
        }
        if ("keep".equals(actionKey) && "consolidate".equals(other.actionKey)) {
            return 0.0d;
        }
        if ("consolidate".equals(actionKey) && "keep".equals(other.actionKey)) {
            return 0.0d;
        }
        return jaccard(actionTokens(), other.actionTokens());
    }

    public boolean polarityCompatible(SemanticCore other) {
        if (polarity == Polarity.UNKNOWN || other.polarity == Polarity.UNKNOWN) {
            return true;
        }
        return polarity == other.polarity;
    }

    public boolean scopeCompatible(SemanticCore other) {
        if ("general".equals(scopeKey) || "general".equals(other.scopeKey)) {
            return true;
        }
        return scopeKey.equals(other.scopeKey);
    }

    private Set<String> actionTokens() {
        Set<String> out = new LinkedHashSet<>();
        for (String t : tokens) {
            if (t.contains("birles") || t.contains("promise") || t.contains("zorunlu")
                    || t.contains("degistir") || t.contains("kalacak") || t.contains("sabit")) {
                out.add(t);
            }
        }
        if (out.isEmpty()) {
            out.add(actionKey);
        }
        return out;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0d;
        }
        Set<String> inter = new LinkedHashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new LinkedHashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) {
            return 0.0d;
        }
        return (double) inter.size() / (double) union.size();
    }
}
