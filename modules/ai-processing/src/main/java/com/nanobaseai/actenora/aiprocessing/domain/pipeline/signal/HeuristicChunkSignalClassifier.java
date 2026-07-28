package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Cheap local classifier for the uncertain band — not an LLM.
 * Uses feature ratios + light structural cues beyond the primary dictionary gate.
 */
public final class HeuristicChunkSignalClassifier implements ChunkSignalClassifier {

    private static final Pattern BUSINESS_OBJECT = Pattern.compile(
            "(?iu)\\b(api|sözleşme|filtre|deploy|sprint|paket|kanal|smoke|maliyet|onay|teslim|"
                    + "release|contract|backlog|regresyon|vtt)\\b"
    );
    private static final Pattern EN_FUTURE = Pattern.compile("(?iu)\\b(we\\s+will|i'll|shall)\\b");

    @Override
    public SignalStrength classify(
            TranscriptChunk chunk,
            ChunkContext context,
            ChunkSignalFeatures features,
            double normalizedScore
    ) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(features, "features");
        String text = chunk.joinedContent() == null ? "" : chunk.joinedContent();
        String lower = text.toLowerCase(Locale.ROOT);

        int positiveAxes =
                (features.explicitDecisionMarkers() > 0 ? 1 : 0)
                        + (features.assignmentStructures() > 0 ? 1 : 0)
                        + (features.commitmentStructures() > 0 ? 1 : 0)
                        + (features.riskExpressions() > 0 ? 1 : 0)
                        + (features.mitigationExpressions() > 0 ? 1 : 0)
                        + (features.openQuestionStructures() > 0 ? 1 : 0)
                        + (features.deadlineExpressions() > 0 ? 1 : 0)
                        + (features.moneyExpressions() > 0 ? 1 : 0);

        boolean hasBusinessObject = BUSINESS_OBJECT.matcher(lower).find();
        // Token suffix scan — never use \\w+(suffix) regex (catastrophic backtracking on TR text).
        boolean hasFuture = EN_FUTURE.matcher(lower).find() || hasTurkishFutureOrImperative(lower);
        boolean noiseDominant = features.uiNoiseCount() >= Math.max(2, features.meaningfulSegmentCount())
                && positiveAxes == 0;
        boolean metaOnly = features.negatedDecisionExpressions() > 0
                && positiveAxes == 0
                && !hasBusinessObject;

        if (noiseDominant || metaOnly) {
            return SignalStrength.LOW_SIGNAL;
        }
        if (positiveAxes >= 3 || (positiveAxes >= 2 && hasBusinessObject && hasFuture)) {
            return SignalStrength.STRONG_SIGNAL;
        }
        if (positiveAxes >= 1 && (hasBusinessObject || hasFuture || features.deadlineExpressions() > 0)) {
            return SignalStrength.POSSIBLE_SIGNAL;
        }
        if (normalizedScore > 0 && hasBusinessObject && features.meaningfulSegmentCount() > 0) {
            return SignalStrength.POSSIBLE_SIGNAL;
        }
        return SignalStrength.LOW_SIGNAL;
    }

    static boolean hasTurkishFutureOrImperative(String lower) {
        for (String token : lower.split("[^\\p{L}\\p{N}]+")) {
            if (token.length() < 4 || token.length() > 48) {
                continue;
            }
            if (token.endsWith("acağız") || token.endsWith("eceğiz")
                    || token.endsWith("acak") || token.endsWith("ecek")
                    || token.endsWith("alsın") || token.endsWith("yapsın")
                    || token.endsWith("bitirsin")) {
                return true;
            }
        }
        return false;
    }
}
