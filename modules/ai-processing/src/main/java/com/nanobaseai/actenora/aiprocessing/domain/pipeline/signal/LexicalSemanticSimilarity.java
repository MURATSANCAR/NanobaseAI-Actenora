package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

/**
 * Bag-of-words cosine similarity for semantic repetition (no embedding model).
 */
public final class LexicalSemanticSimilarity {

    private LexicalSemanticSimilarity() {
    }

    public static double cosine(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0.0d;
        }
        // Bound work for pathological long segments (gate must stay cheap).
        String left = a.length() > 2_000 ? a.substring(0, 2_000) : a;
        String right = b.length() > 2_000 ? b.substring(0, 2_000) : b;
        String[] ta = tokenize(left);
        String[] tb = tokenize(right);
        if (ta.length == 0 || tb.length == 0) {
            return 0.0d;
        }
        java.util.Map<String, Integer> fa = freq(ta);
        java.util.Map<String, Integer> fb = freq(tb);
        double dot = 0.0d;
        double na = 0.0d;
        double nb = 0.0d;
        for (var e : fa.entrySet()) {
            double va = e.getValue();
            na += va * va;
            Integer vb = fb.get(e.getKey());
            if (vb != null) {
                dot += va * vb;
            }
        }
        for (var e : fb.entrySet()) {
            double vb = e.getValue();
            nb += vb * vb;
        }
        if (na == 0.0d || nb == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static String[] tokenize(String text) {
        return text.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]+", " ")
                .trim()
                .split("\\s+");
    }

    private static java.util.Map<String, Integer> freq(String[] tokens) {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        for (String t : tokens) {
            if (t.length() < 3) {
                continue;
            }
            map.merge(t, 1, Integer::sum);
        }
        return map;
    }
}
