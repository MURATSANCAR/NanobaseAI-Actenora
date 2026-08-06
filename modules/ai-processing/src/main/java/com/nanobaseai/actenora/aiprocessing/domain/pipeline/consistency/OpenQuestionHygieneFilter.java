package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingNoisePatterns;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Generic open-question hygiene: drop speculation, in-meeting chat/facilitation questions,
 * logistics, and questions already answered by decisions/commitments/actions.
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

    /**
     * Mid-meeting chat / engagement / rhetorical prompts — never meeting-outcome open questions.
     */
    private static final Pattern CONVERSATIONAL_CHAT = Pattern.compile(
            "(?iu)("
                    + "kafan[ıi]za|akl[ıi]n[ıi]za|"
                    + "anlad[ıi]n[ıi]z\\s+m[ıi]|takip\\s+(?:edebildiniz|ettiniz)|"
                    + "çok\\s+mu\\s+doğru|öyle\\s+mi\\b|değil\\s+mi\\b|"
                    + "(?:bi+\\s+|bir\\s+)?şey\\s+var\\s+m[ıi]|"
                    + "neler\\s+yap[ıi]labilir|hangi\\s+[oö]rnekler|"
                    + "sorunuz\\s+var\\s+m[ıi]|sorum\\s+var\\s+m[ıi]|"
                    + "anlatay[ıi]m\\s+m[ıi]|g[oö]stereyim\\s+mi|soray[ıi]m\\s+m[ıi]|"
                    + "ne\\s+dersiniz|ne\\s+d[uü][sş][uü]n[uü]yorsunuz|"
                    + "dinliyorsunuz\\s+mu|duydunuz\\s+mu|"
                    + "\\bhani\\b|yani\\s+o\\s+anlamda|"
                    + "biraz\\s+a[cç]al[ıi]m|bu\\s+noktay[ıi]\\s+a[cç]"
                    + ")"
    );

    /** Thin confirmation / fragment questions with no follow-up substance. */
    private static final Pattern THIN_CONFIRMATION = Pattern.compile(
            "(?iu)^\\s*(?:çok\\s+mu\\s+doğru|doğru\\s+mu|öyle\\s+mi|değil\\s+mi|"
                    + "anlad[ıi]n[ıi]z\\s+m[ıi]|geriden\\s+mi\\s+gelecek|"
                    + "var\\s+m[ıi]|yok\\s+mu)\\s*\\??\\s*$"
    );

    private static final Pattern UNRESOLVED_OUTCOME = Pattern.compile(
            "(?iu)(a[cç][ıi]k\\s+kalan|netle[sş]medi|cevaps[ıi]z|"
                    + "ne\\s+zaman|ne\\s+a[sş]amada|"
                    + "kim\\s+(?:sorumlu|g[oö]nderecek|[uü]stlenecek|koordine)|"
                    + "maliyet|b[uü]t[cç]e|kapsam|teslim|deadline|owner|"
                    + "rollback|geri\\s+alma|k[oö]k\\s+neden|"
                    + "hangi\\s+(?:alarm|dashboard|metrik|log|kriter|ortam|versiyon)|"
                    + "nas[ıi]l\\s+(?:[cç][oö]z[uü]lecek|netle[sş]ecek|ilerleyecek)|"
                    + "hen[uü]z\\s+net|takip\\s+gerektir)"
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
            if (shouldDropAsNonOutcome(q.text())
                    || answeredInMeeting(q, decisions, actions, commitments)) {
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

    /**
     * True for chatty / logistics / speculative text that must never become an open-question outcome.
     */
    public static boolean shouldDropAsNonOutcome(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        if (looksLikeSpeculation(text)
                || looksLikeConversationalChat(text)
                || isThinChatFragment(text)
                || MeetingNoisePatterns.isMeetingLogistics(text)
                || MeetingNoisePatterns.isFacilitationOrPreparation(text)) {
            return true;
        }
        return false;
    }

    static boolean looksLikeSpeculation(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        return SPECULATION.matcher(text).find();
    }

    static boolean looksLikeConversationalChat(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        return CONVERSATIONAL_CHAT.matcher(text).find();
    }

    static boolean isThinChatFragment(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String t = text.strip();
        if (THIN_CONFIRMATION.matcher(t).matches()) {
            return true;
        }
        // Short fragments without an unresolved-outcome cue are mid-chat, not minutes outcomes.
        return t.length() < 28 && !UNRESOLVED_OUTCOME.matcher(t).find();
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
