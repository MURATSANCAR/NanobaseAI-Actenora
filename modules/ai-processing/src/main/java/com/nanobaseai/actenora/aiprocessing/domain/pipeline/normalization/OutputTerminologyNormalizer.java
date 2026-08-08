package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

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

import java.util.List;
import java.util.Objects;

/**
 * Applies the terminology glossary ({@link MeetingTerminologyNormalizer}) to the FINAL note's
 * user-facing text. Input segments are already normalized before extraction, but the LLM can still
 * emit a wrong surface form in its generated prose/items (e.g. a company name it hallucinated in the
 * summary). Running the same deterministic glossary over the output is the safety net that makes
 * sector/company/technical terms come out right regardless of where the error was introduced.
 */
public final class OutputTerminologyNormalizer {

    private final MeetingTerminologyNormalizer terms;

    public OutputTerminologyNormalizer() {
        this(MeetingTerminologyNormalizer.productionDefaults());
    }

    public OutputTerminologyNormalizer(MeetingTerminologyNormalizer terms) {
        this.terms = Objects.requireNonNull(terms, "terms");
    }

    public FinalNoteDraft apply(FinalNoteDraft d) {
        if (d == null) {
            return null;
        }
        return new FinalNoteDraft(
                r(d.executiveSummary()),
                map(d.decisions(), x -> new DecisionCandidate(
                        r(x.text()), x.evidenceSegmentIds(), x.confidence(), r(x.rationale()), x.status())),
                map(d.actionItems(), x -> new ActionItemCandidate(
                        r(x.text()), x.owner(), x.dueDate(), x.evidenceSegmentIds(), x.confidence(),
                        x.ownerType(), x.priority(), x.relativeDate(), x.dueAt())),
                map(d.risks(), x -> new RiskCandidate(
                        r(x.text()), x.evidenceSegmentIds(), x.confidence(), x.likelihood(), r(x.mitigation()))),
                map(d.openQuestions(), x -> new OpenQuestionCandidate(
                        r(x.text()), x.evidenceSegmentIds(), x.confidence())),
                map(d.commitments(), x -> new CommitmentCandidate(
                        r(x.text()), x.owner(), x.evidenceSegmentIds(), x.confidence())),
                map(d.topics(), x -> new TopicCandidate(
                        r(x.text()), r(x.summary()), x.evidenceSegmentIds(), x.confidence())),
                map(d.issues(), x -> new IssueCandidate(
                        r(x.text()), x.evidenceSegmentIds(), x.confidence())),
                map(d.proposals(), x -> new ProposalCandidate(
                        r(x.text()), x.evidenceSegmentIds(), x.confidence())),
                map(d.importantFacts(), x -> new ImportantFactCandidate(
                        r(x.text()), x.evidenceSegmentIds(), x.confidence())),
                d.qualityFlags(),
                d.evidenceSegmentIds(),
                d.confidence(),
                d.requiresManualReview());
    }

    private String r(String s) {
        return s == null ? null : terms.rewrite(s);
    }

    private static <T> List<T> map(List<T> in, java.util.function.UnaryOperator<T> f) {
        return in == null ? List.of() : in.stream().map(f).toList();
    }
}
