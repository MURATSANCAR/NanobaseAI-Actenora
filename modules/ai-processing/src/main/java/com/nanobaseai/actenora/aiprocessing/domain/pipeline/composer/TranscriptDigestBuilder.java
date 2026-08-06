package com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TokenEstimator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds an evidence-aware {@link TranscriptDigest}.
 * Production path: deterministic linguistic harvest over token windows (map-reduce style
 * aggregation that always preserves evidenceSegmentIds). Optional LLM-per-window digest is
 * intentionally deferred — free-text reduce must not drop segment ids.
 */
public final class TranscriptDigestBuilder {

    public static final String KIND_MEETING_CHARACTER = "MEETING_CHARACTER";
    public static final String KIND_FUTURE_COMMITMENT = "FUTURE_COMMITMENT";
    public static final String KIND_SELECTION_CONFIRMATION = "SELECTION_CONFIRMATION";
    public static final String KIND_UNRESOLVED_QUESTION = "UNRESOLVED_QUESTION";
    public static final String KIND_HISTORICAL_NARRATION = "HISTORICAL_NARRATION";

    private static final Pattern FUTURE = Pattern.compile(
            "(?iu)(eyl[uü]l|\\b\\d{1,2}\\s*(ocak|[sş]ubat|mart|nisan|may[ıi]s|haziran|temmuz|"
                    + "a[gğ]ustos|eyl[uü]l|ekim|kas[ıi]m|aral[ıi]k)\\b|"
                    + "[oö]n[uü]m[uü]zdeki\\s+(hafta|ay|d[oö]nem)|"
                    + "tekrar\\s+(g[oö]r[uü][sş]|konu[sş]|de[gğ]erlendir)|"
                    + "(yapaca[gğ][ıi]z|edece[gğ]iz|ba[sş]lataca[gğ][ıi]z|planl[ıi]yoruz))"
    );
    private static final Pattern SELECTION = Pattern.compile(
            "(?iu)((?:ile\\s+)?devam\\s+edece[gğ]iz|se[cç]tik|se[cç]ildi|tercih\\s+ettik|"
                    + "anla[sş]maya\\s+vard[ıi]k|we\\s+will\\s+continue\\s+with)"
    );
    private static final Pattern HISTORICAL = Pattern.compile(
            "(?iu)(daha\\s+[oö]nce|[oö]nceden|ge[cç]mi[sş]te|\\d+\\s*(?:ayl[ıi]k|y[ıi]ll[ıi]k)|"
                    + "previously|earlier)"
    );
    private static final Pattern CHARACTER = Pattern.compile(
            "(?iu)(tan[ıi][sş]ma|vizyon|kapasite|kabiliyet|i[sş]\\s+birli[gğ]i|"
                    + "introduction|vision|capabilities)"
    );
    private static final Pattern QUESTION = Pattern.compile("(?iu)(\\?|\\b(nedir|nas[ıi]l|ne\\s+zaman|kim)\\b)");
    private static final Pattern TEMPORAL_CAPTURE = Pattern.compile(
            "(?iu)(eyl[uü]l|[oö]n[uü]m[uü]zdeki\\s+(?:hafta|ay)|\\d{1,2}[./]\\d{1,2}(?:[./]\\d{2,4})?)"
    );

    private final TokenEstimator tokenEstimator;
    private final int windowTargetTokens;

    public TranscriptDigestBuilder() {
        this(TokenEstimator.approximate(), 3_500);
    }

    public TranscriptDigestBuilder(TokenEstimator tokenEstimator, int windowTargetTokens) {
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "tokenEstimator");
        if (windowTargetTokens <= 0) {
            throw new IllegalArgumentException("windowTargetTokens must be > 0");
        }
        this.windowTargetTokens = windowTargetTokens;
    }

    public TranscriptDigest build(List<SegmentInput> segments) {
        Objects.requireNonNull(segments, "segments");
        if (segments.isEmpty()) {
            return new TranscriptDigest(List.of(), List.of(), List.of(), new TranscriptDigest.SegmentRange(0, 0));
        }

        Map<String, TranscriptDigest.DigestSignal> signals = new LinkedHashMap<>();
        Map<String, TranscriptDigest.DigestFact> facts = new LinkedHashMap<>();
        Map<String, TranscriptDigest.DigestFact> questions = new LinkedHashMap<>();

        int fromSeq = Integer.MAX_VALUE;
        int toSeq = 0;
        int windowTokens = 0;
        List<SegmentInput> window = new ArrayList<>();

        for (SegmentInput segment : segments) {
            fromSeq = Math.min(fromSeq, segment.sequence());
            toSeq = Math.max(toSeq, segment.sequence());
            int tokens = tokenEstimator.estimate(segment.content() == null ? "" : segment.content());
            if (!window.isEmpty() && windowTokens + tokens > windowTargetTokens) {
                harvest(window, signals, facts, questions);
                window = new ArrayList<>();
                windowTokens = 0;
            }
            window.add(segment);
            windowTokens += tokens;
        }
        if (!window.isEmpty()) {
            harvest(window, signals, facts, questions);
        }

        if (fromSeq == Integer.MAX_VALUE) {
            fromSeq = 0;
        }
        return new TranscriptDigest(
                List.copyOf(signals.values()),
                List.copyOf(facts.values()),
                List.copyOf(questions.values()),
                new TranscriptDigest.SegmentRange(fromSeq, toSeq)
        );
    }

    private static void harvest(
            List<SegmentInput> window,
            Map<String, TranscriptDigest.DigestSignal> signals,
            Map<String, TranscriptDigest.DigestFact> facts,
            Map<String, TranscriptDigest.DigestFact> questions
    ) {
        for (SegmentInput segment : window) {
            String text = segment.content() == null ? "" : segment.content().strip();
            if (text.isBlank()) {
                continue;
            }
            String id = segment.segmentId();
            String speaker = segment.speakerDisplayName();
            String lower = text.toLowerCase(Locale.ROOT);

            if (CHARACTER.matcher(text).find()) {
                putSignal(signals, KIND_MEETING_CHARACTER,
                        "Tanışma / vizyon / kapasite değerlendirmesi sinyali", id);
            }
            if (SELECTION.matcher(text).find()) {
                putFact(facts, KIND_SELECTION_CONFIRMATION, text, speaker, null, id);
            }
            if (FUTURE.matcher(text).find()) {
                String temporal = captureTemporal(text);
                putFact(facts, KIND_FUTURE_COMMITMENT, text, speaker, temporal, id);
            }
            if (HISTORICAL.matcher(text).find() && !SELECTION.matcher(text).find()) {
                putFact(facts, KIND_HISTORICAL_NARRATION, text, speaker, null, id);
            }
            if (QUESTION.matcher(text).find() && text.contains("?")) {
                putFact(questions, KIND_UNRESOLVED_QUESTION, text, speaker, null, id);
            }
            // Keep compiler quiet for unused lower in future cue expansion.
            Objects.requireNonNull(lower);
        }
    }

    private static String captureTemporal(String text) {
        Matcher m = TEMPORAL_CAPTURE.matcher(text);
        return m.find() ? m.group() : null;
    }

    private static void putSignal(
            Map<String, TranscriptDigest.DigestSignal> signals,
            String kind,
            String text,
            String evidenceId
    ) {
        TranscriptDigest.DigestSignal existing = signals.get(kind);
        if (existing == null) {
            signals.put(kind, new TranscriptDigest.DigestSignal(kind, text, List.of(evidenceId)));
            return;
        }
        List<String> ids = new ArrayList<>(existing.evidenceSegmentIds());
        if (!ids.contains(evidenceId)) {
            ids.add(evidenceId);
        }
        signals.put(kind, new TranscriptDigest.DigestSignal(kind, existing.text(), ids));
    }

    private static void putFact(
            Map<String, TranscriptDigest.DigestFact> facts,
            String kind,
            String text,
            String speaker,
            String temporal,
            String evidenceId
    ) {
        String key = kind + "|" + normalizeKey(text);
        TranscriptDigest.DigestFact existing = facts.get(key);
        if (existing == null) {
            facts.put(key, new TranscriptDigest.DigestFact(kind, text, speaker, temporal, List.of(evidenceId)));
            return;
        }
        List<String> ids = new ArrayList<>(existing.evidenceSegmentIds());
        if (!ids.contains(evidenceId)) {
            ids.add(evidenceId);
        }
        String speakerKeep = existing.speaker() == null || existing.speaker().isBlank() ? speaker : existing.speaker();
        String temporalKeep = existing.temporalExpression() == null ? temporal : existing.temporalExpression();
        facts.put(key, new TranscriptDigest.DigestFact(kind, existing.text(), speakerKeep, temporalKeep, ids));
    }

    private static String normalizeKey(String text) {
        String t = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
        return t.length() <= 120 ? t : t.substring(0, 120);
    }
}
