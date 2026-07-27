package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingNoisePatterns;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Cheap structural + behavioural feature extraction. Dictionary hits contribute counts only.
 */
public final class StructuralChunkSignalFeatureExtractor implements ChunkSignalFeatureExtractor {

    private static final Pattern DATE_LIKE = Pattern.compile(
            "(?iu)\\b(\\d{1,2}[./]\\d{1,2}([./]\\d{2,4})?|\\d{4}-\\d{2}-\\d{2})\\b"
    );
    private static final Pattern MONEY_LIKE = Pattern.compile(
            "(?iu)\\b\\d{1,3}([.,]\\d{3})*([.,]\\d+)?\\s*(tl|usd|eur|₺)\\b|%\\s*\\d+|\\d+\\s*%"
    );
    private static final Pattern PERSON_TASK = Pattern.compile(
            "(?iu)\\b([A-ZÇĞİÖŞÜ][a-zçğıöşü]{2,})\\b.{0,40}\\b(alsın|yapsın|hazırlasın|bıraksın|bitirsin|göndersin)\\b"
    );
    private static final Pattern CLOSE_TOPIC = Pattern.compile(
            "(?iu)(bu\\s+nokta(yı)?\\s+.*(kapat|kapan)|konuyu\\s+kapattık|değişmeyecek|donduruyoruz|dondurulacak)"
    );
    private static final Pattern META_DECISION_TALK = Pattern.compile(
            "(?iu)(karar\\s+ekran|kararları\\s+(tekrar\\s+)?oku|mevcut\\s+kararları\\s+oku|karar\\s+listesi)"
    );
    private static final Pattern MITIGATION_CONT = Pattern.compile(
            "(?iu)(bunun\\s+(için|önlemi)|önlem\\s+olarak|erken\\s+smoke|mitigation)"
    );
    private static final Pattern OPEN_Q = Pattern.compile("(?iu)(\\?|kim\\s+onay|açık\\s+soru|who\\s+will)");

    private final String fallbackDictionaryVersion;
    private final java.util.concurrent.ConcurrentHashMap<String, SignalDictionary> dictionaryCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    public StructuralChunkSignalFeatureExtractor(SignalDictionary dictionary) {
        this.fallbackDictionaryVersion = dictionary.version();
        dictionaryCache.put(dictionary.version(), dictionary);
    }

    public StructuralChunkSignalFeatureExtractor(SignalGateConfig config) {
        this.fallbackDictionaryVersion = config.dictionaryVersion();
    }

    private SignalDictionary dictionaryFor(ChunkContext context) {
        String key = context.language() + ":" + context.config().dictionaryVersion();
        return dictionaryCache.computeIfAbsent(key, k ->
                SignalDictionary.loadForLanguage(context.language(), context.config().dictionaryVersion()));
    }

    @Override
    public ChunkSignalFeatures extract(TranscriptChunk chunk, ChunkContext context) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(context, "context");
        SignalDictionary dictionary = dictionaryFor(context);
        int decisions = 0;
        int assignments = 0;
        int commitments = 0;
        int deadlines = 0;
        int money = 0;
        int risks = 0;
        int mitigations = 0;
        int questions = 0;
        int proposals = 0;
        int negated = 0;
        int uiNoise = 0;
        int meaningful = 0;
        int repetitions = 0;

        List<SegmentInput> segments = chunk.segments();
        String prevNorm = null;
        Set<String> tokens = new HashSet<>();
        int tokenCount = 0;

        for (SegmentInput segment : segments) {
            String raw = segment.content() == null ? "" : segment.content().strip();
            if (raw.isEmpty()) {
                continue;
            }
            String lower = raw.toLowerCase(Locale.ROOT);
            boolean lowSignal = MeetingNoisePatterns.isLowSignalSegment(raw);
            if (!lowSignal) {
                meaningful++;
            }

            int decisionHit = dictionary.decisionVerbHits(lower);
            if (CLOSE_TOPIC.matcher(lower).find()) {
                decisionHit++;
            }
            if (META_DECISION_TALK.matcher(lower).find()) {
                // Talking about the decision UI — not a business decision.
                decisionHit = 0;
                uiNoise++;
            }
            decisions += decisionHit;

            int assignHit = dictionary.assignmentHits(lower);
            if (PERSON_TASK.matcher(raw).find()) {
                assignHit++;
            }
            assignments += assignHit;
            commitments += dictionary.commitmentHits(lower);

            int deadlineHit = dictionary.deadlineHits(lower);
            if (DATE_LIKE.matcher(lower).find()) {
                deadlineHit++;
            }
            deadlines += deadlineHit;

            int moneyHit = dictionary.moneyHits(lower);
            if (MONEY_LIKE.matcher(lower).find()) {
                moneyHit++;
            }
            money += moneyHit;

            risks += dictionary.riskHits(lower);
            mitigations += dictionary.mitigationHits(lower);
            if (MITIGATION_CONT.matcher(lower).find()) {
                mitigations++;
            }

            int qHit = dictionary.openQuestionHits(lower);
            if (OPEN_Q.matcher(lower).find()) {
                qHit++;
            }
            questions += qHit;

            proposals += dictionary.proposalHits(lower);
            negated += dictionary.negatedDecisionHits(lower);
            uiNoise += dictionary.uiNoiseHits(lower);
            if (lowSignal) {
                uiNoise++;
            }

            String norm = lower.replaceAll("\\s+", " ");
            if (prevNorm != null) {
                double sim = Math.max(
                        jaccard(prevNorm, norm),
                        LexicalSemanticSimilarity.cosine(prevNorm, norm)
                );
                if (sim >= 0.72d) {
                    repetitions++;
                }
            }
            prevNorm = norm;

            for (String t : norm.split(" ")) {
                if (t.length() > 2) {
                    tokens.add(t);
                    tokenCount++;
                }
            }
        }

        int continuation = 0;
        if (context.config().continuationAware() && context.previous().isPresent()) {
            ChunkSignalSummary prev = context.previous().get();
            String joined = chunk.joinedContent().toLowerCase(Locale.ROOT);
            if (prev.hasRiskSignal() && (mitigations > 0 || MITIGATION_CONT.matcher(joined).find())) {
                continuation++;
            }
            if (prev.hasActionSignal() && (deadlines > 0 || commitments > 0 || assignments > 0)) {
                continuation++;
            }
            if (prev.hasOpenQuestionSignal() && (decisions > 0 || commitments > 0)) {
                continuation++;
            }
            if (prev.hasDecisionSignal() && (assignments > 0 || deadlines > 0)) {
                continuation++;
            }
        }
        if (context.config().continuationAware() && context.nextPreview().isPresent()) {
            ChunkSignalSummary next = context.nextPreview().get();
            // Keep risk/open-question chunk if the next window carries mitigation/answer.
            if (risks > 0 && next.hasMitigationSignal()) {
                continuation++;
            }
            if (questions > 0 && (next.hasDecisionSignal() || next.hasActionSignal())) {
                continuation++;
            }
        }

        double diversity = tokenCount == 0 ? 0.0d : (double) tokens.size() / (double) tokenCount;
        double repetitionRatio = segments.isEmpty()
                ? 0.0d
                : (double) repetitions / (double) segments.size();
        if (!context.config().semanticRepetitionEnabled()) {
            repetitionRatio = 0.0d;
            repetitions = 0;
        }

        return new ChunkSignalFeatures(
                decisions,
                assignments,
                commitments,
                deadlines,
                money,
                risks,
                mitigations,
                questions,
                proposals,
                negated,
                repetitions,
                uiNoise,
                continuation,
                diversity,
                repetitionRatio,
                Math.max(meaningful, 0)
        );
    }

    private static double jaccard(String a, String b) {
        Set<String> sa = Set.of(a.split(" "));
        Set<String> sb = Set.of(b.split(" "));
        if (sa.isEmpty() && sb.isEmpty()) {
            return 1.0d;
        }
        int inter = 0;
        for (String t : sa) {
            if (sb.contains(t)) {
                inter++;
            }
        }
        int union = sa.size() + sb.size() - inter;
        return union == 0 ? 0.0d : (double) inter / (double) union;
    }
}
