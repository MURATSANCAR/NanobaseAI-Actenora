package com.nanobaseai.actenora.aiprocessing.domain.pipeline.attribution;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic attribution/classification corrections applied AFTER LLM finalization, using the
 * transcript's speaker labels + segment order as ground truth. The 3B-active local model reliably
 * captures WHAT was said but frequently mis-attributes WHO (owner) or mis-classifies items; these
 * are exactly the errors a human reviewer catches. Rather than trust the model's reasoning, we
 * verify each item against the evidence segments it is grounded on:
 *
 * <ol>
 *   <li><b>Self-commitment owner</b>: if an action/commitment is grounded on a first-person-future
 *       utterance ("...göndereceğim"), the owner is the SPEAKER of that segment — override the
 *       model's guess. (Fixes "Onur said he'd send the PDF" mislabeled as Gökhan.)</li>
 *   <li><b>Answered / logistics open questions</b>: drop an open question that was answered within a
 *       few following segments by another speaker, or that is meeting logistics (mic/camera). (Fixes
 *       "is there a bank using this?" — answered "no" — being listed as open.)</li>
 *   <li><b>Prior decisions</b>: a "decision" whose evidence uses past-tense / prior-meeting markers
 *       is not THIS meeting's decision — move it to importantFacts. (Fixes the Simple supplier
 *       selection, decided before the meeting.)</li>
 * </ol>
 */
public final class AttributionGroundingPostProcessor {

    private static final int ANSWER_LOOKAHEAD_SEGMENTS = 4;

    private static final Pattern SELF_COMMIT = Pattern.compile(
            "(göndereceğim|yapacağım|ayarlayacağım|hazırlayacağım|paylaşacağım|ileteceğim|"
                    + "alacağım|edeceğim|çalışacağım|bakacağım|tamamlayacağım|kuracağım|vereceğim|"
                    + "organize edeceğim|iletirim|gönderirim|hazırlarım)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PRIOR_DECISION = Pattern.compile(
            "(seçtik|seçmiştir|seçmişti|yaptık|verdik|almıştık|belirledik|karar verdik|"
                    + "geçmişte|daha önce|başlangıç toplant|aylık\\s+\\S*\\s*süreç|süreç yaşadık|"
                    + "devam edeceğiz|kararlaştır\\w*|tamamlanmış\\w*)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern LOGISTICS_Q = Pattern.compile(
            "(mikrofon|kamera|ses(i|iniz|im)?\\b|bağlant|katılım sağla|toplantıya\\s+\\S*\\s*katıl|"
                    + "ekran payla|duyuluyor|geliyor mu|açık mı|kapalı|yankı)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern ANSWER_START = Pattern.compile(
            "^\\s*(yok|hayır|evet|var\\b|tabii|doğru|değil|olmaz|olur|kesinlikle|maalesef|"
                    + "elbette|aynen|malesef)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public FinalNoteDraft apply(FinalNoteDraft draft, List<SegmentInput> segments, Set<String> roster) {
        if (draft == null || segments == null || segments.isEmpty()) {
            return draft;
        }
        Map<String, SegmentInput> byId = new LinkedHashMap<>();
        Map<String, Integer> posById = new LinkedHashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            SegmentInput s = segments.get(i);
            if (s != null && s.segmentId() != null) {
                byId.put(s.segmentId(), s);
                posById.put(s.segmentId(), i);
            }
        }

        boolean[] changed = {false};

        List<ActionItemCandidate> actions = new ArrayList<>();
        for (ActionItemCandidate a : draft.actionItems()) {
            actions.add(correctActionOwner(a, byId, changed));
        }
        List<CommitmentCandidate> commitments = new ArrayList<>();
        for (CommitmentCandidate c : draft.commitments()) {
            commitments.add(correctCommitmentOwner(c, byId, changed));
        }

        List<OpenQuestionCandidate> questions = new ArrayList<>();
        for (OpenQuestionCandidate q : draft.openQuestions()) {
            if (shouldDropQuestion(q, byId, posById, segments)) {
                changed[0] = true;
                continue;
            }
            questions.add(q);
        }

        List<DecisionCandidate> decisions = new ArrayList<>();
        List<ImportantFactCandidate> facts = new ArrayList<>(draft.importantFacts());
        for (DecisionCandidate d : draft.decisions()) {
            if (isPriorDecision(d, byId)) {
                facts.add(new ImportantFactCandidate(d.text(), d.evidenceSegmentIds(), d.confidence()));
                changed[0] = true;
                continue;
            }
            decisions.add(d);
        }

        if (!changed[0]) {
            return draft;
        }
        List<String> flags = new ArrayList<>(draft.qualityFlags());
        if (flags.stream().noneMatch(f -> f != null && f.equalsIgnoreCase("ATTRIBUTION_CORRECTED"))) {
            flags.add("ATTRIBUTION_CORRECTED");
        }
        return new FinalNoteDraft(
                draft.executiveSummary(),
                decisions,
                actions,
                draft.risks(),
                questions,
                commitments,
                draft.topics(),
                draft.issues(),
                draft.proposals(),
                facts,
                flags,
                draft.evidenceSegmentIds(),
                draft.confidence(),
                draft.requiresManualReview());
    }

    private ActionItemCandidate correctActionOwner(
            ActionItemCandidate a, Map<String, SegmentInput> byId, boolean[] changed) {
        String speaker = selfCommitSpeaker(a.evidenceSegmentIds(), byId);
        if (speaker != null && !speaker.equalsIgnoreCase(nz(a.owner()))) {
            changed[0] = true;
            return new ActionItemCandidate(
                    a.text(), speaker, a.dueDate(), a.evidenceSegmentIds(), a.confidence(),
                    a.ownerType(), a.priority(), a.relativeDate(), a.dueAt());
        }
        return a;
    }

    private CommitmentCandidate correctCommitmentOwner(
            CommitmentCandidate c, Map<String, SegmentInput> byId, boolean[] changed) {
        String speaker = selfCommitSpeaker(c.evidenceSegmentIds(), byId);
        if (speaker != null && !speaker.equalsIgnoreCase(nz(c.owner()))) {
            changed[0] = true;
            return new CommitmentCandidate(c.text(), speaker, c.evidenceSegmentIds(), c.confidence());
        }
        return c;
    }

    /** The speaker of the first evidence segment whose content is a first-person-future commitment. */
    private String selfCommitSpeaker(List<String> evidenceIds, Map<String, SegmentInput> byId) {
        if (evidenceIds == null) {
            return null;
        }
        for (String id : evidenceIds) {
            SegmentInput s = byId.get(id);
            if (s == null || s.content() == null) {
                continue;
            }
            if (SELF_COMMIT.matcher(s.content()).find()) {
                String name = cleanSpeaker(s.speakerDisplayName());
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        }
        return null;
    }

    private boolean shouldDropQuestion(
            OpenQuestionCandidate q,
            Map<String, SegmentInput> byId,
            Map<String, Integer> posById,
            List<SegmentInput> segments) {
        String qText = nz(q.text());
        if (LOGISTICS_Q.matcher(qText).find()) {
            return true;
        }
        int lastPos = -1;
        String askerSpeaker = null;
        if (q.evidenceSegmentIds() != null) {
            for (String id : q.evidenceSegmentIds()) {
                Integer p = posById.get(id);
                if (p != null && p > lastPos) {
                    lastPos = p;
                    SegmentInput s = byId.get(id);
                    askerSpeaker = s == null ? null : cleanSpeaker(s.speakerDisplayName());
                }
                SegmentInput s = byId.get(id);
                if (s != null && s.content() != null && LOGISTICS_Q.matcher(s.content()).find()) {
                    return true;
                }
            }
        }
        if (lastPos < 0) {
            return false;
        }
        int end = Math.min(segments.size(), lastPos + 1 + ANSWER_LOOKAHEAD_SEGMENTS);
        for (int i = lastPos + 1; i < end; i++) {
            SegmentInput s = segments.get(i);
            if (s == null || s.content() == null) {
                continue;
            }
            String sp = cleanSpeaker(s.speakerDisplayName());
            boolean differentSpeaker = sp == null || askerSpeaker == null || !sp.equalsIgnoreCase(askerSpeaker);
            if (differentSpeaker && ANSWER_START.matcher(s.content()).find()) {
                return true; // answered in the meeting -> not an open question
            }
        }
        return false;
    }

    private boolean isPriorDecision(DecisionCandidate d, Map<String, SegmentInput> byId) {
        if (d.text() != null && PRIOR_DECISION.matcher(d.text()).find()) {
            return true;
        }
        if (d.evidenceSegmentIds() != null) {
            for (String id : d.evidenceSegmentIds()) {
                SegmentInput s = byId.get(id);
                if (s != null && s.content() != null && PRIOR_DECISION.matcher(s.content()).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Strip a trailing role parenthetical: "Gökhan ERTÜRK (GENEL MÜDÜR)" -> "Gökhan ERTÜRK". */
    private static String cleanSpeaker(String display) {
        if (display == null) {
            return null;
        }
        String d = display.strip();
        int paren = d.indexOf('(');
        if (paren > 0) {
            d = d.substring(0, paren).strip();
        }
        return d.isBlank() ? null : d;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
