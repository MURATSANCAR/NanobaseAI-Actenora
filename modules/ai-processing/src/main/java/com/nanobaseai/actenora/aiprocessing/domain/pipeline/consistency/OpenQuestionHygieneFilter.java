package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Generic open-question hygiene: drop speculation / third-party gossip and questions that were
 * already answered in the same meeting by decisions, commitments, or action text.
 * No meeting-specific hardcodes — cues are linguistic/structural.
 */
public final class OpenQuestionHygieneFilter {

    public static final String OPEN_QUESTION_HYGIENE_DROPPED = "OPEN_QUESTION_HYGIENE_DROPPED";

    private static final Pattern SPECULATION = Pattern.compile(
            "(?iu)(acaba|yoksa|belki|san[ıi]r[ıi]m|galiba|"
                    + "tercihindeki\\s+etki|"
                    + "rol[uü]\\s+(?:veya|ve)\\s+|"
                    + "(?:etki|katk[ıi])(?:si|sı)?\\s+(?:nedir|ne)|"
                    + "mi\\s+se[cç]tiniz|m[ıi]\\s+se[cç]tiniz|"
                    + "rumor|gossip|speculat)"
    );
    private static final Pattern ANSWERED_YES_NO = Pattern.compile(
            "(?iu)\\b(var\\s+m[ıi]|yok\\s+mu|do[gğ]ru\\s+mu|m[uü]mk[uü]n\\s+m[uü])\\b"
    );

    public List<OpenQuestionCandidate> filter(
            List<OpenQuestionCandidate> questions,
            List<DecisionCandidate> decisions,
            List<ActionItemCandidate> actions,
            List<CommitmentCandidate> commitments,
            List<String> qualityFlagsOut
    ) {
        Objects.requireNonNull(questions, "questions");
        List<OpenQuestionCandidate> kept = new ArrayList<>();
        int dropped = 0;
        for (OpenQuestionCandidate q : questions) {
            if (looksLikeSpeculation(q.text()) || answeredInMeeting(q, decisions, actions, commitments)) {
                dropped++;
                continue;
            }
            kept.add(q);
        }
        if (dropped > 0 && qualityFlagsOut != null) {
            qualityFlagsOut.add(OPEN_QUESTION_HYGIENE_DROPPED);
        }
        return List.copyOf(kept);
    }

    static boolean looksLikeSpeculation(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        return SPECULATION.matcher(text).find();
    }

    private static boolean answeredInMeeting(
            OpenQuestionCandidate question,
            List<DecisionCandidate> decisions,
            List<ActionItemCandidate> actions,
            List<CommitmentCandidate> commitments
    ) {
        SemanticCore qCore = SemanticCore.extract(ItemTextViews.comparisonCore(question.text()));
        for (DecisionCandidate d : nullSafe(decisions)) {
            if (answers(qCore, d.text(), d.confidence())) {
                return true;
            }
        }
        for (ActionItemCandidate a : nullSafe(actions)) {
            if (answers(qCore, a.text(), a.confidence())) {
                return true;
            }
        }
        for (CommitmentCandidate c : nullSafe(commitments)) {
            if (answers(qCore, c.text(), c.confidence())) {
                return true;
            }
        }
        // Yes/no inventory questions answered by explicit negative/positive in decision text.
        if (ANSWERED_YES_NO.matcher(question.text()).find()) {
            for (DecisionCandidate d : nullSafe(decisions)) {
                String dt = d.text() == null ? "" : d.text().toLowerCase(Locale.ROOT);
                if (dt.contains("yok") || dt.contains("var") || dt.contains("no ") || dt.contains("none")) {
                    SemanticCore dCore = SemanticCore.extract(ItemTextViews.comparisonCore(d.text()));
                    if (dCore.topicSimilarity(qCore) >= 0.55d) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean answers(SemanticCore question, String answerText, double confidence) {
        if (answerText == null || answerText.isBlank() || confidence < 0.80d) {
            return false;
        }
        SemanticCore aCore = SemanticCore.extract(ItemTextViews.comparisonCore(answerText));
        return aCore.topicSimilarity(question) >= 0.78d
                && aCore.actionSimilarity(question) >= 0.45d
                && aCore.scopeCompatible(question)
                && aCore.polarityCompatible(question);
    }

    private static <T> List<T> nullSafe(List<T> items) {
        return items == null ? List.of() : items;
    }
}
