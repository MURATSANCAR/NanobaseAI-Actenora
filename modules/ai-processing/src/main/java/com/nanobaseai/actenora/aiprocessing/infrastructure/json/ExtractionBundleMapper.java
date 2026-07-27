package com.nanobaseai.actenora.aiprocessing.infrastructure.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.IssueCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;

import java.util.ArrayList;
import java.util.List;

public final class ExtractionBundleMapper {

    public ExtractionBundle fromJson(JsonNode node) {
        return new ExtractionBundle(
                mapTopics(node.get("topics")),
                mapDecisions(node.get("decisions")),
                mapActions(node.get("actionItems")),
                mapRisks(node.get("risks")),
                mapQuestions(node.get("openQuestions")),
                mapCommitments(node.get("commitments")),
                mapIssues(node.get("issues")),
                mapProposals(node.get("proposals")),
                mapFacts(node.get("importantFacts")),
                mapStrings(node.get("qualityFlags")),
                mapStrings(node.get("evidenceSegmentIds")),
                node.has("confidence") && !node.get("confidence").isNull()
                        ? node.get("confidence").asDouble()
                        : 0.0d
        );
    }

    private List<TopicCandidate> mapTopics(JsonNode array) {
        List<TopicCandidate> list = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return list;
        }
        for (JsonNode item : array) {
            list.add(new TopicCandidate(
                    item.get("text").asText(),
                    mapStrings(item.get("evidenceSegmentIds")),
                    confidenceOrDefault(item)
            ));
        }
        return list;
    }

    private List<DecisionCandidate> mapDecisions(JsonNode array) {
        List<DecisionCandidate> list = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return list;
        }
        for (JsonNode item : array) {
            list.add(new DecisionCandidate(
                    item.get("text").asText(),
                    mapStrings(item.get("evidenceSegmentIds")),
                    confidenceOrDefault(item)
            ));
        }
        return list;
    }

    private List<ActionItemCandidate> mapActions(JsonNode array) {
        List<ActionItemCandidate> list = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return list;
        }
        for (JsonNode item : array) {
            list.add(new ActionItemCandidate(
                    item.get("text").asText(),
                    textOrNull(item, "owner"),
                    textOrNull(item, "dueDate"),
                    mapStrings(item.get("evidenceSegmentIds")),
                    confidenceOrDefault(item)
            ));
        }
        return list;
    }

    private List<RiskCandidate> mapRisks(JsonNode array) {
        List<RiskCandidate> list = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return list;
        }
        for (JsonNode item : array) {
            list.add(new RiskCandidate(
                    item.get("text").asText(),
                    mapStrings(item.get("evidenceSegmentIds")),
                    confidenceOrDefault(item)
            ));
        }
        return list;
    }

    private List<OpenQuestionCandidate> mapQuestions(JsonNode array) {
        List<OpenQuestionCandidate> list = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return list;
        }
        for (JsonNode item : array) {
            list.add(new OpenQuestionCandidate(
                    item.get("text").asText(),
                    mapStrings(item.get("evidenceSegmentIds")),
                    confidenceOrDefault(item)
            ));
        }
        return list;
    }

    private List<CommitmentCandidate> mapCommitments(JsonNode array) {
        List<CommitmentCandidate> list = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return list;
        }
        for (JsonNode item : array) {
            list.add(new CommitmentCandidate(
                    item.get("text").asText(),
                    textOrNull(item, "owner"),
                    mapStrings(item.get("evidenceSegmentIds")),
                    confidenceOrDefault(item)
            ));
        }
        return list;
    }

    private List<IssueCandidate> mapIssues(JsonNode array) {
        List<IssueCandidate> list = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return list;
        }
        for (JsonNode item : array) {
            list.add(new IssueCandidate(
                    item.get("text").asText(),
                    mapStrings(item.get("evidenceSegmentIds")),
                    confidenceOrDefault(item)
            ));
        }
        return list;
    }

    private List<ProposalCandidate> mapProposals(JsonNode array) {
        List<ProposalCandidate> list = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return list;
        }
        for (JsonNode item : array) {
            list.add(new ProposalCandidate(
                    item.get("text").asText(),
                    mapStrings(item.get("evidenceSegmentIds")),
                    confidenceOrDefault(item)
            ));
        }
        return list;
    }

    private List<ImportantFactCandidate> mapFacts(JsonNode array) {
        List<ImportantFactCandidate> list = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return list;
        }
        for (JsonNode item : array) {
            list.add(new ImportantFactCandidate(
                    item.get("text").asText(),
                    mapStrings(item.get("evidenceSegmentIds")),
                    confidenceOrDefault(item)
            ));
        }
        return list;
    }

    private static List<String> mapStrings(JsonNode array) {
        List<String> list = new ArrayList<>();
        if (array == null || array.isNull() || !array.isArray()) {
            return list;
        }
        for (JsonNode item : array) {
            list.add(item.asText());
        }
        return list;
    }

    private static String textOrNull(JsonNode item, String field) {
        if (!item.has(field) || item.get(field).isNull()) {
            return null;
        }
        return item.get(field).asText();
    }

    private static double confidenceOrDefault(JsonNode item) {
        if (!item.has("confidence") || item.get("confidence").isNull()) {
            return 0.7d;
        }
        return item.get("confidence").asDouble();
    }
}
