#!/usr/bin/env python3
"""Inject observability-only lineage hooks into a base-commit worktree.

Does not rewrite extraction keep/drop logic; only adds imports + record() calls
and lineage install/persist around extractionPipeline.run.
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path


def ensure_import(text: str, import_line: str) -> str:
    if import_line in text:
        return text
    # after package line / first imports block
    m = re.search(r"(?m)^import .+", text)
    if not m:
        return text
    idx = m.start()
    return text[:idx] + import_line + "\n" + text[idx:]


def patch_action_post(path: Path) -> None:
    if not path.exists():
        return
    t = path.read_text()
    if "LineageSupport" in t and "ACTION_COMPOUND_SPLIT" in t:
        return
    imports = [
        "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.ItemLineageRecord;",
        "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageOperation;",
        "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageReasonCode;",
        "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageStage;",
        "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageSupport;",
    ]
    for imp in imports:
        t = ensure_import(t, imp)
    needle = "if (decomposition.split()) {\n                stats.incrementCompoundActionsSplit(decomposition.actions().size());\n            }"
    # variants
    patterns = [
        (
            r"if \(decomposition\.split\(\)\) \{\s*stats\.incrementCompoundActionsSplit\(decomposition\.actions\(\)\.size\(\)\);\s*\}",
            """if (decomposition.split()) {
                stats.incrementCompoundActionsSplit(decomposition.actions().size());
                String parentId = LineageSupport.idOf("action", prefixed.text(), prefixed.evidenceSegmentIds());
                for (ActionItemCandidate child : decomposition.actions()) {
                    LineageSupport.record(
                            LineageSupport.idOf("action", child.text(), child.evidenceSegmentIds()),
                            "ACTION_ITEM",
                            LineageStage.ACTION_COMPOUND_DECOMPOSITION,
                            LineageOperation.SPLIT,
                            LineageReasonCode.ACTION_COMPOUND_SPLIT,
                            List.of(parentId),
                            parentId,
                            ItemLineageRecord.snapshot(prefixed.text(), prefixed.owner(), prefixed.relativeDate(),
                                    prefixed.evidenceSegmentIds()),
                            ItemLineageRecord.snapshot(child.text(), child.owner(), child.relativeDate(),
                                    child.evidenceSegmentIds()),
                            "action-compound-split-v1",
                            null,
                            null,
                            null
                    );
                }
            }""",
        )
    ]
    for pat, repl in patterns:
        nt, n = re.subn(pat, repl, t, count=1, flags=re.S)
        if n:
            t = nt
            break
    else:
        print(f"WARN: could not inject compound split lineage into {path}")
    path.write_text(t)


def patch_crosstype(path: Path) -> None:
    if not path.exists():
        return
    t = path.read_text()
    if "recordLineageObservability" in t:
        return
    # If CrossType exists without lineage, append a no-op-safe call is hard.
    # Prefer copying observe helper only when apply() ends with return new Outcome
    if "LineageSupport" in t:
        return
    print(f"INFO: CrossType present at {path} — ensuring lineage imports; full hook may already differ")
    # Leave existing file if complex; build from A already has CrossType with possible hooks from overlay copy
    # Overlay: copy CrossType from ROOT if label is A_LINEAGE — handled by caller optionally


def patch_executor(path: Path) -> None:
    if not path.exists():
        return
    t = path.read_text()
    if "persistItemLineageArtifact" in t:
        return
    t = ensure_import(t, "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.ItemLineageRecorder;")
    # Add field if missing
    if "lineageRecordingEnabled" not in t:
        t = t.replace(
            "private final int parallelChunkLimit;",
            "private final int parallelChunkLimit;\n    private final boolean lineageRecordingEnabled;",
            1,
        )
        # Assign false in existing primary constructor end — find last assignment of parallelChunkLimit
        t = t.replace(
            "this.parallelChunkLimit = parallelChunkLimit;\n    }",
            "this.parallelChunkLimit = parallelChunkLimit;\n        this.lineageRecordingEnabled = false;\n    }",
            1,
        )
    # Wrap extractionPipeline.run
    old = "PipelineRunResult result = extractionPipeline.run("
    if old in t and "ItemLineageRecorder.install" not in t:
        # crude wrap: replace assignment start
        t = t.replace(
            old,
            """PipelineRunResult result;
        boolean __lin = lineageRecordingEnabled
                || "true".equalsIgnoreCase(System.getenv("ACTENORA_MEETING_LINEAGE_ENABLED"))
                || "true".equalsIgnoreCase(System.getenv("ACTENORA_AI_PIPELINE_LINEAGE_RECORDING"));
        if (__lin) {
            ItemLineageRecorder.install(ItemLineageRecorder.enabled());
        }
        try {
            result = extractionPipeline.run(""",
            1,
        )
        # close after run call's trailing );  — find first ");\n\n        if (result.success())"
        t = t.replace(
            ");\n\n        if (result.success()) {",
            """);
        } finally {
            if (__lin) {
                persistItemLineageArtifact(job);
                ItemLineageRecorder.clear();
            }
        }

        if (result.success()) {""",
            1,
        )
    if "persistItemLineageArtifact" not in t:
        helper = '''
    private void persistItemLineageArtifact(AiJob job) {
        if (artifacts == null || job == null) {
            return;
        }
        try {
            ItemLineageRecorder recorder = ItemLineageRecorder.current();
            if (!recorder.isEnabled() || recorder.size() == 0) {
                return;
            }
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("schemaVersion", "1.0");
            payload.put("meetingId", job.meetingOccurrenceId() == null ? null : job.meetingOccurrenceId().toString());
            payload.put("jobId", job.id() == null ? null : job.id().toString());
            payload.put("events", recorder.toSafeMaps());
            String json = artifactMapper.writeValueAsString(payload);
            artifacts.save(ProcessingArtifact.inlineJson(
                    job.tenantId(),
                    job.id(),
                    job.meetingOccurrenceId(),
                    ItemLineageRecorder.ARTIFACT_TYPE,
                    json,
                    Instant.now()
            ));
        } catch (Exception ignored) {
        }
    }
'''
        # insert before usesExtractionPipeline
        t = t.replace(
            "private static boolean usesExtractionPipeline(",
            helper + "\n    private static boolean usesExtractionPipeline(",
            1,
        )
    path.write_text(t)


def patch_pipeline_props(path: Path) -> None:
    if not path.exists():
        return
    t = path.read_text()
    if "lineageRecordingEnabled" in t and "isLineageRecordingEnabled" in t:
        return
    if "lineageRecordingEnabled" not in t:
        t = t.replace(
            "private String finalizationFailureMode;",
            "private String finalizationFailureMode;\n    private boolean lineageRecordingEnabled;",
            1,
        )
    if "isLineageRecordingEnabled" not in t:
        # insert before final closing brace
        idx = t.rfind("}")
        if idx < 0:
            return
        methods = """
    public boolean isLineageRecordingEnabled() {
        return lineageRecordingEnabled;
    }

    public void setLineageRecordingEnabled(boolean lineageRecordingEnabled) {
        this.lineageRecordingEnabled = lineageRecordingEnabled;
    }

"""
        t = t[:idx] + methods + t[idx:]
    path.write_text(t)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", type=Path, required=True)
    ap.add_argument("--label", required=True)
    args = ap.parse_args()
    root = args.root
    patch_action_post(
        root
        / "modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/domain/pipeline/action/ActionPostProcessingPipeline.java"
    )
    ct = root / "modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/domain/pipeline/consistency/CrossTypeMeetingItemSubsumer.java"
    if ct.exists():
        # Overlay current CrossType lineage-aware version only if we can keep behavior:
        # For A, CrossType already exists — inject observe if missing by copying observe method is risky.
        # Instead ensure package lineage compiles; CrossType without hooks still OK for action-split focus.
        patch_crosstype(ct)
    patch_executor(
        root
        / "modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/application/execution/AiJobInferenceExecutor.java"
    )
    patch_pipeline_props(
        root
        / "apps/platform-backend/src/main/java/com/nanobaseai/actenora/security/aiprocessing/AiPipelineProperties.java"
    )
    print("inject done for", args.label, root)


if __name__ == "__main__":
    main()
