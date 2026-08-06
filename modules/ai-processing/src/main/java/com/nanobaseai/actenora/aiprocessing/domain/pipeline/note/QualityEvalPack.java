package com.nanobaseai.actenora.aiprocessing.domain.pipeline.note;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.IssueCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;

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
        return build(
                tenantId, jobId, meetingOccurrenceId, transcriptId, noteId,
                servedModelId, promptVersion, schemaVersion, draft, createdAt, null
        );
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
            Instant createdAt,
            FinalizationProvenance finalization
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
        counts.put("topics", size(draft.topics()));
        counts.put("issues", size(draft.issues()));
        counts.put("proposals", size(draft.proposals()));
        root.put("counts", counts);

        root.put("requiresManualReview", draft.requiresManualReview());
        root.put("confidence", draft.confidence());
        root.put("qualityFlags", List.copyOf(draft.qualityFlags()));
        root.put("executiveSummary", draft.executiveSummary() == null ? "" : draft.executiveSummary());
        if (finalization != null) {
            root.put("finalization", finalization.toMap());
        }

        root.put("decisions", mapDecisions(draft.decisions()));
        root.put("actionItems", mapActions(draft.actionItems()));
        root.put("risks", mapRisks(draft.risks()));
        root.put("openQuestions", mapQuestions(draft.openQuestions()));
        root.put("commitments", mapCommitments(draft.commitments()));
        root.put("topics", mapTopics(draft.topics()));
        root.put("issues", mapIssues(draft.issues()));
        root.put("proposals", mapProposals(draft.proposals()));
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

    private static List<Map<String, Object>> mapCommitments(List<CommitmentCandidate> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (CommitmentCandidate item : items) {
            Map<String, Object> mapped = baseItem(
                    item.text(), item.confidence(), item.evidenceSegmentIds());
            mapped.put("owner", item.owner());
            out.add(mapped);
        }
        return out;
    }

    private static List<Map<String, Object>> mapTopics(List<TopicCandidate> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items != null) {
            for (TopicCandidate item : items) {
                out.add(baseItem(item.text(), item.confidence(), item.evidenceSegmentIds()));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> mapIssues(List<IssueCandidate> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items != null) {
            for (IssueCandidate item : items) {
                out.add(baseItem(item.text(), item.confidence(), item.evidenceSegmentIds()));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> mapProposals(List<ProposalCandidate> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items != null) {
            for (ProposalCandidate item : items) {
                out.add(baseItem(item.text(), item.confidence(), item.evidenceSegmentIds()));
            }
        }
        return out;
    }

    private static Map<String, Object> baseItem(
            String text,
            double confidence,
            List<String> evidenceSegmentIds
    ) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("text", text);
        mapped.put("confidence", confidence);
        mapped.put("evidenceSegmentIds", evidenceSegmentIds);
        return mapped;
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
