package com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * High-precision deterministic speech-act matcher backed by a versioned rule registry.
 */
public final class DeterministicSpeechActMatcher {

    private final List<Rule> rules;

    public DeterministicSpeechActMatcher(List<Rule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    public static DeterministicSpeechActMatcher loadDefaultTr() {
        return load("/aiprocessing/speechact/speech-act-rules-tr-v1.json");
    }

    public static DeterministicSpeechActMatcher load(String classpath) {
        try (InputStream in = DeterministicSpeechActMatcher.class.getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IllegalArgumentException("Speech-act rules not found: " + classpath);
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(in);
            List<Rule> loaded = new ArrayList<>();
            for (JsonNode node : root.path("rules")) {
                loaded.add(new Rule(
                        node.path("id").asText(),
                        MeetingSpeechAct.valueOf(node.path("speechAct").asText()),
                        Pattern.compile(node.path("pattern").asText()),
                        node.path("confidence").asDouble(0.9d)
                ));
            }
            loaded.sort(Comparator.comparingDouble(Rule::confidence).reversed());
            return new DeterministicSpeechActMatcher(loaded);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load speech-act rules: " + classpath, ex);
        }
    }

    public SpeechActResult classify(String text) {
        if (text == null || text.isBlank()) {
            return SpeechActResult.unknown();
        }
        String sample = text.strip();
        Optional<Rule> match = rules.stream()
                .filter(rule -> rule.pattern().matcher(sample).find())
                .findFirst();
        if (match.isEmpty()) {
            return SpeechActResult.unknown();
        }
        Rule rule = match.get();
        return new SpeechActResult(
                rule.speechAct(),
                rule.confidence(),
                ClassificationSource.DETERMINISTIC_RULE,
                rule.id()
        );
    }

    public boolean hasExplicitDecisionCue(String text) {
        return classify(text).speechAct() == MeetingSpeechAct.EXPLICIT_DECISION;
    }

    public boolean hasProposalCue(String text) {
        return classify(text).speechAct() == MeetingSpeechAct.PROPOSAL_CUE
                || (text != null && text.toLowerCase(Locale.ROOT).contains("henüz karar değil"));
    }

    public record Rule(String id, MeetingSpeechAct speechAct, Pattern pattern, double confidence) {
        public Rule {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(speechAct, "speechAct");
            Objects.requireNonNull(pattern, "pattern");
        }
    }
}
