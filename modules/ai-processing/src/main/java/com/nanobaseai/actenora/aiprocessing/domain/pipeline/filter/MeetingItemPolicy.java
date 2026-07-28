package com.nanobaseai.actenora.aiprocessing.domain.pipeline.filter;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact.MeetingSpeechAct;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact.SpeechActResult;

import java.util.Locale;
import java.util.Objects;

/**
 * Central keep/drop/review policy: item type × speech-act (never substring-only).
 */
public final class MeetingItemPolicy {

    public PolicyAction decide(MeetingItemType type, SpeechActResult speechAct, String text) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(speechAct, "speechAct");
        MeetingSpeechAct act = speechAct.speechAct();
        return switch (type) {
            case IMPORTANT_FACT -> switch (act) {
                case STATUS_QUO, DISCUSSION_PROMPT, NOTE_INSTRUCTION, CLOSING_META -> PolicyAction.DROP;
                default -> PolicyAction.KEEP;
            };
            case OPEN_QUESTION -> act == MeetingSpeechAct.DISCUSSION_PROMPT
                    || isVagueDiscussion(text)
                    ? PolicyAction.DROP
                    : PolicyAction.KEEP;
            case TOPIC -> switch (act) {
                case DISCUSSION_PROMPT, CLOSING_META, STATUS_QUO, NOTE_INSTRUCTION -> PolicyAction.DROP;
                default -> isVagueTopic(text) ? PolicyAction.DROP : PolicyAction.KEEP;
            };
            case COMMITMENT -> switch (act) {
                case NOTE_INSTRUCTION, CLOSING_META, STATUS_QUO, DISCUSSION_PROMPT -> PolicyAction.DROP;
                default -> PolicyAction.KEEP;
            };
            case ACTION -> switch (act) {
                case NOTE_INSTRUCTION, CLOSING_META -> PolicyAction.DROP;
                case STATUS_QUO -> PolicyAction.KEEP; // do not blind-drop; "mevcut X güncelle" is valid
                default -> PolicyAction.KEEP;
            };
            case DECISION -> decideDecision(act);
            case PROPOSAL -> act == MeetingSpeechAct.DISCUSSION_PROMPT
                    || act == MeetingSpeechAct.STATUS_QUO
                    || act == MeetingSpeechAct.CLOSING_META
                    ? PolicyAction.DROP
                    : PolicyAction.KEEP;
            case ISSUE, RISK -> PolicyAction.KEEP;
        };
    }

    private static PolicyAction decideDecision(MeetingSpeechAct act) {
        if (act == MeetingSpeechAct.EXPLICIT_DECISION) {
            return PolicyAction.KEEP;
        }
        if (act == MeetingSpeechAct.STATUS_QUO
                || act == MeetingSpeechAct.DISCUSSION_PROMPT
                || act == MeetingSpeechAct.PROPOSAL_CUE
                || act == MeetingSpeechAct.NOTE_INSTRUCTION
                || act == MeetingSpeechAct.CLOSING_META) {
            return PolicyAction.DROP;
        }
        return PolicyAction.KEEP;
    }

    private static boolean isVagueDiscussion(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("açmamız")
                || t.contains("açalım")
                || t.contains("noktanın detay")
                || t.contains("açıklanması gereken")
                || t.contains("detaylandırılması");
    }

    private static boolean isVagueTopic(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String t = text.toLowerCase(Locale.ROOT).strip();
        return t.contains("bağlam paylaş")
                || t.contains("noktanın açıl")
                || t.contains("noktayı aç")
                || t.contains("detaylandır")
                || t.contains("kapanış")
                || t.contains("açmamız")
                || t.contains("açalım")
                || t.length() < 8;
    }
}
