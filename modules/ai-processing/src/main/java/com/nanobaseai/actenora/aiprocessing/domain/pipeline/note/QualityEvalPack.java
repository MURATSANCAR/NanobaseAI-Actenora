package com.nanobaseai.actenora.aiprocessing.domain.pipeline.note;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Auto-persisted quality snapshot for every successful minutes finalization.
 * No manual capture checklist — produced by the normal pipeline flow.
 */
public final class QualityEvalPack {

    public static final String ARTIFACT_TYPE = "quality-eval-pack";
    public static final String SCHEMA_VERSION = "1.0";

    private QualityEvalPack() {
    }

    public static Map<String, Object> build(
            UUID tenantId,
            UUID jobId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            UUID noteId,
            String servedModelId,
            String promptVersion,
            String schemaVersion,
            FinalNoteDraft draft,
            Instant createdAt
    ) {
        Objects.requireNonNull(draft, "draft");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("artifactType", ARTIFACT_TYPE);
        root.put("createdAt", createdAt == null ? Instant.now().toString() : createdAt.toString());

        Map<String, Object> ids = new LinkedHashMap<>();
        ids.put("tenantId", str(tenantId));
        ids.put("jobId", str(jobId));
        ids.put("meetingOccurrenceId", str(meetingOccurrenceId));
        ids.put("transcriptId", str(transcriptId));
        ids.put("noteId", str(noteId));
        root.put("ids", ids);

        Map<String, Object> deploy = new LinkedHashMap<>();
        deploy.put("servedModelId", nullToEmpty(servedModelId));
        deploy.put("promptVersion", nullToEmpty(promptVersion));
        deploy.put("schemaVersion", nullToEmpty(schemaVersion));
        root.put("deploy", deploy);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("decisions", size(draft.decisions()));
        counts.put("actionItems", size(draft.actionItems()));
        counts.put("risks", size(draft.risks()));
        counts.put("openQuestions", size(draft.openQuestions()));
        counts.put("importantFacts", size(draft.importantFacts()));
        counts.put("commitments", size(draft.commitments()));
        counts.put("proposals", size(draft.proposals()));
        root.put("counts", counts);

        root.put("requiresManualReview", draft.requiresManualReview());
        root.put("confidence", draft.confidence());
        root.put("qualityFlags", List.copyOf(draft.qualityFlags()));
        root.put("executiveSummary", draft.executiveSummary() == null ? "" : draft.executiveSummary());

        root.put("decisions", mapDecisions(draft.decisions()));
        root.put("actionItems", mapActions(draft.actionItems()));
        root.put("risks", mapRisks(draft.risks()));
        root.put("openQuestions", mapQuestions(draft.openQuestions()));
        root.put("importantFacts", mapFacts(draft.importantFacts()));

        return root;
    }

    private static List<Map<String, Object>> mapDecisions(List<DecisionCandidate> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (DecisionCandidate d : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", d.text());
            m.put("confidence", d.confidence());
            m.put("evidenceSegmentIds", d.evidenceSegmentIds());
            m.put("status", d.status());
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> mapActions(List<ActionItemCandidate> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (ActionItemCandidate a : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", a.text());
            m.put("owner", a.owner());
            m.put("dueDate", a.dueDate());
            m.put("relativeDate", a.relativeDate());
            m.put("dueAt", a.dueAt());
            m.put("confidence", a.confidence());
            m.put("evidenceSegmentIds", a.evidenceSegmentIds());
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> mapRisks(List<RiskCandidate> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (RiskCandidate r : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", r.text());
            m.put("confidence", r.confidence());
            m.put("evidenceSegmentIds", r.evidenceSegmentIds());
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> mapQuestions(List<OpenQuestionCandidate> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (OpenQuestionCandidate q : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", q.text());
            m.put("confidence", q.confidence());
            m.put("evidenceSegmentIds", q.evidenceSegmentIds());
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> mapFacts(List<ImportantFactCandidate> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (ImportantFactCandidate f : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", f.text());
            m.put("confidence", f.confidence());
            m.put("evidenceSegmentIds", f.evidenceSegmentIds());
            out.add(m);
        }
        return out;
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static String str(UUID id) {
        return id == null ? "" : id.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
