package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Versioned helper lexicon — contributes feature scores, never hard-gates alone.
 */
public final class SignalDictionary {

    private final String version;
    private final List<String> decisionVerbs;
    private final List<String> assignmentCues;
    private final List<String> commitmentCues;
    private final List<String> deadlineCues;
    private final List<String> riskCues;
    private final List<String> mitigationCues;
    private final List<String> moneyCues;
    private final List<String> openQuestionCues;
    private final List<String> proposalCues;
    private final List<String> negatedDecisionCues;
    private final List<String> uiNoiseCues;

    public SignalDictionary(
            String version,
            List<String> decisionVerbs,
            List<String> assignmentCues,
            List<String> commitmentCues,
            List<String> deadlineCues,
            List<String> riskCues,
            List<String> mitigationCues,
            List<String> moneyCues,
            List<String> openQuestionCues,
            List<String> proposalCues,
            List<String> negatedDecisionCues,
            List<String> uiNoiseCues
    ) {
        this.version = Objects.requireNonNull(version, "version");
        this.decisionVerbs = List.copyOf(decisionVerbs);
        this.assignmentCues = List.copyOf(assignmentCues);
        this.commitmentCues = List.copyOf(commitmentCues);
        this.deadlineCues = List.copyOf(deadlineCues);
        this.riskCues = List.copyOf(riskCues);
        this.mitigationCues = List.copyOf(mitigationCues);
        this.moneyCues = List.copyOf(moneyCues);
        this.openQuestionCues = List.copyOf(openQuestionCues);
        this.proposalCues = List.copyOf(proposalCues);
        this.negatedDecisionCues = List.copyOf(negatedDecisionCues);
        this.uiNoiseCues = List.copyOf(uiNoiseCues);
    }

    public String version() {
        return version;
    }

    public static SignalDictionary load(String dictionaryVersion) {
        String path = "/aiprocessing/signal/dictionaries/" + dictionaryVersion + ".json";
        try (InputStream in = SignalDictionary.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Signal dictionary not found: " + path);
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(in);
            return new SignalDictionary(
                    text(root, "version", dictionaryVersion),
                    strings(root, "decisionVerbs"),
                    strings(root, "assignmentCues"),
                    strings(root, "commitmentCues"),
                    strings(root, "deadlineCues"),
                    strings(root, "riskCues"),
                    strings(root, "mitigationCues"),
                    strings(root, "moneyCues"),
                    strings(root, "openQuestionCues"),
                    strings(root, "proposalCues"),
                    strings(root, "negatedDecisionCues"),
                    strings(root, "uiNoiseCues")
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load signal dictionary " + dictionaryVersion, ex);
        }
    }

    /**
     * Language-first dictionary with fallback to configured version (typically tr-en-v1).
     */
    public static SignalDictionary loadForLanguage(String language, String fallbackVersion) {
        String lang = language == null ? "tr" : language.trim().toLowerCase(Locale.ROOT);
        String primary = lang.startsWith("en") ? "en-v1" : "tr-v1";
        try {
            return load(primary);
        } catch (RuntimeException primaryMiss) {
            try {
                return load(fallbackVersion == null || fallbackVersion.isBlank() ? "tr-en-v1" : fallbackVersion);
            } catch (RuntimeException fallbackMiss) {
                return load("tr-en-v1");
            }
        }
    }

    int countHits(String haystackLower, List<String> cues) {
        int hits = 0;
        for (String cue : cues) {
            if (cue != null && !cue.isBlank() && haystackLower.contains(cue.toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        return hits;
    }

    int decisionVerbHits(String textLower) {
        return countHits(textLower, decisionVerbs);
    }

    int assignmentHits(String textLower) {
        return countHits(textLower, assignmentCues);
    }

    int commitmentHits(String textLower) {
        return countHits(textLower, commitmentCues);
    }

    int deadlineHits(String textLower) {
        return countHits(textLower, deadlineCues);
    }

    int riskHits(String textLower) {
        return countHits(textLower, riskCues);
    }

    int mitigationHits(String textLower) {
        return countHits(textLower, mitigationCues);
    }

    int moneyHits(String textLower) {
        return countHits(textLower, moneyCues);
    }

    int openQuestionHits(String textLower) {
        return countHits(textLower, openQuestionCues);
    }

    int proposalHits(String textLower) {
        return countHits(textLower, proposalCues);
    }

    int negatedDecisionHits(String textLower) {
        return countHits(textLower, negatedDecisionCues);
    }

    int uiNoiseHits(String textLower) {
        return countHits(textLower, uiNoiseCues);
    }

    private static String text(JsonNode root, String field, String fallback) {
        JsonNode n = root.get(field);
        return n == null || n.isNull() || n.asText().isBlank() ? fallback : n.asText();
    }

    private static List<String> strings(JsonNode root, String field) {
        JsonNode arr = root.get(field);
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                if (n != null && !n.asText("").isBlank()) {
                    out.add(n.asText().trim());
                }
            }
        }
        return out;
    }
}
