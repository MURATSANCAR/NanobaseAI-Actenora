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
    m = re.search(r"(?m)^import .+", text)
    if not m:
        return text
    idx = m.start()
    return text[:idx] + import_line + "\n" + text[idx:]


def ensure_lineage_imports(text: str) -> str:
    for imp in (
        "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.ItemLineageRecord;",
        "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageOperation;",
        "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageReasonCode;",
        "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageStage;",
        "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageSupport;",
    ):
        text = ensure_import(text, imp)
    if "import java.util.List;" not in text and "java.util.List" not in text:
        text = ensure_import(text, "import java.util.List;")
    return text


def patch_action_post(path: Path) -> None:
    if not path.exists():
        return
    t = path.read_text()
    t = ensure_lineage_imports(t)

    # --- compound SPLIT (idempotent) ---
    if "ACTION_COMPOUND_SPLIT" not in t:
        pat = (
            r"if \(decomposition\.split\(\)\) \{\s*"
            r"stats\.incrementCompoundActionsSplit\(decomposition\.actions\(\)\.size\(\)\);\s*"
            r"\}"
        )
        repl = """if (decomposition.split()) {
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
            } else {
                LineageSupport.record(
                        LineageSupport.idOf("action", prefixed.text(), prefixed.evidenceSegmentIds()),
                        "ACTION_ITEM",
                        LineageStage.ACTION_COMPOUND_DECOMPOSITION,
                        LineageOperation.KEEP,
                        LineageReasonCode.ACTION_COMPOUND_NOT_SPLIT,
                        List.of(),
                        null,
                        ItemLineageRecord.snapshot(prefixed.text(), prefixed.owner(), prefixed.relativeDate(),
                                prefixed.evidenceSegmentIds()),
                        ItemLineageRecord.snapshot(prefixed.text(), prefixed.owner(), prefixed.relativeDate(),
                                prefixed.evidenceSegmentIds()),
                        "action-compound-split-v1",
                        null,
                        null,
                        null
                );
            }"""
        nt, n = re.subn(pat, repl, t, count=1, flags=re.S)
        if n:
            t = nt
        else:
            print(f"WARN: could not inject compound split lineage into {path}")

    # If SPLIT exists but no NOT_SPLIT else-branch, add KEEP-not-split after the split block.
    if "ACTION_COMPOUND_NOT_SPLIT" not in t and "ACTION_COMPOUND_SPLIT" in t:
        # After split block that ends before working.addAll
        marker = "working.addAll(decomposition.actions());"
        if marker in t and "ACTION_COMPOUND_NOT_SPLIT" not in t:
            # Prefer extending existing if (decomposition.split()) { ... } without else
            old = """if (decomposition.split()) {
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
            }
            working.addAll(decomposition.actions());"""
            new = """if (decomposition.split()) {
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
            } else {
                LineageSupport.record(
                        LineageSupport.idOf("action", prefixed.text(), prefixed.evidenceSegmentIds()),
                        "ACTION_ITEM",
                        LineageStage.ACTION_COMPOUND_DECOMPOSITION,
                        LineageOperation.KEEP,
                        LineageReasonCode.ACTION_COMPOUND_NOT_SPLIT,
                        List.of(),
                        null,
                        ItemLineageRecord.snapshot(prefixed.text(), prefixed.owner(), prefixed.relativeDate(),
                                prefixed.evidenceSegmentIds()),
                        ItemLineageRecord.snapshot(prefixed.text(), prefixed.owner(), prefixed.relativeDate(),
                                prefixed.evidenceSegmentIds()),
                        "action-compound-split-v1",
                        null,
                        null,
                        null
                );
            }
            working.addAll(decomposition.actions());"""
            if old in t:
                t = t.replace(old, new, 1)

    # --- prefix UPDATE ---
    if "ACTION_PREFIX_NORMALIZATION" not in t:
        old = """ActionItemCandidate prefixed = action.withText(stripped);
            CompoundActionDecomposer.Decomposition decomposition ="""
        new = """ActionItemCandidate prefixed = action.withText(stripped);
            if (!stripped.equals(action.text().strip())) {
                LineageSupport.record(
                        LineageSupport.idOf("action", prefixed.text(), prefixed.evidenceSegmentIds()),
                        "ACTION_ITEM",
                        LineageStage.ACTION_PREFIX_NORMALIZATION,
                        LineageOperation.UPDATE,
                        LineageReasonCode.ACTION_OWNER_BOUND,
                        List.of(),
                        null,
                        ItemLineageRecord.snapshot(action.text(), action.owner(), action.relativeDate(),
                                action.evidenceSegmentIds()),
                        ItemLineageRecord.snapshot(prefixed.text(), prefixed.owner(), prefixed.relativeDate(),
                                prefixed.evidenceSegmentIds()),
                        "action-prefix-v1",
                        null,
                        null,
                        null
                );
            }
            CompoundActionDecomposer.Decomposition decomposition ="""
        # Use a dedicated reason — prefer ACTION_COMPOUND_NOT_SPLIT only if no better code;
        # ACTION_OWNER_BOUND is wrong for prefix. Use FLAG with SPEECH_ACT_UNKNOWN? Better add note in ruleVersion.
        # Keep as UPDATE with ACTION_OWNER_BOUND only if LineageReasonCode lacks prefix; use POLICY_KEEP as neutral.
        new = new.replace("LineageReasonCode.ACTION_OWNER_BOUND", "LineageReasonCode.POLICY_KEEP")
        if old in t:
            t = t.replace(old, new, 1)
        else:
            print(f"WARN: could not inject prefix lineage into {path}")

    # --- date binding ---
    if "ACTION_RELATIVE_DATE_BINDING" not in t:
        old = """List<ActionItemCandidate> dated = new ArrayList<>();
        for (ActionItemCandidate action : working) {
            dated.add(resolveDates(action, ctx, stats, flags));
        }"""
        new = """List<ActionItemCandidate> dated = new ArrayList<>();
        for (ActionItemCandidate action : working) {
            ActionItemCandidate afterDate = resolveDates(action, ctx, stats, flags);
            if (!java.util.Objects.equals(action.relativeDate(), afterDate.relativeDate())
                    || !java.util.Objects.equals(action.dueAt(), afterDate.dueAt())
                    || !java.util.Objects.equals(action.dueDate(), afterDate.dueDate())) {
                LineageSupport.record(
                        LineageSupport.idOf("action", afterDate.text(), afterDate.evidenceSegmentIds()),
                        "ACTION_ITEM",
                        LineageStage.ACTION_RELATIVE_DATE_BINDING,
                        LineageOperation.UPDATE,
                        LineageReasonCode.ACTION_DATE_BOUND,
                        List.of(),
                        null,
                        ItemLineageRecord.snapshot(action.text(), action.owner(), action.relativeDate(),
                                action.evidenceSegmentIds()),
                        ItemLineageRecord.snapshot(afterDate.text(), afterDate.owner(), afterDate.relativeDate(),
                                afterDate.evidenceSegmentIds()),
                        "action-date-bind-v1",
                        null,
                        null,
                        null
                );
            }
            dated.add(afterDate);
        }"""
        if old in t:
            t = t.replace(old, new, 1)
        else:
            print(f"WARN: could not inject date-binding lineage into {path}")

    # --- owner binding (compare dated vs ownerSanitized) ---
    if "ACTION_CLAUSE_BINDING" not in t:
        # B: sanitizeUnknownOwners(dated, participants, stats);
        # A: sanitizeUnknownOwners(dated, participants, ctx.transcriptSegments(), stats);
        patterns = [
            (
                "List<ActionItemCandidate> ownerSanitized = sanitizeUnknownOwners(dated, participants, stats);",
                """List<ActionItemCandidate> ownerSanitized = sanitizeUnknownOwners(dated, participants, stats);
        for (int __i = 0; __i < dated.size() && __i < ownerSanitized.size(); __i++) {
            ActionItemCandidate beforeOwner = dated.get(__i);
            ActionItemCandidate afterOwner = ownerSanitized.get(__i);
            if (!java.util.Objects.equals(beforeOwner.owner(), afterOwner.owner())) {
                LineageSupport.record(
                        LineageSupport.idOf("action", afterOwner.text(), afterOwner.evidenceSegmentIds()),
                        "ACTION_ITEM",
                        LineageStage.ACTION_CLAUSE_BINDING,
                        LineageOperation.UPDATE,
                        LineageReasonCode.ACTION_OWNER_BOUND,
                        List.of(),
                        null,
                        ItemLineageRecord.snapshot(beforeOwner.text(), beforeOwner.owner(), beforeOwner.relativeDate(),
                                beforeOwner.evidenceSegmentIds()),
                        ItemLineageRecord.snapshot(afterOwner.text(), afterOwner.owner(), afterOwner.relativeDate(),
                                afterOwner.evidenceSegmentIds()),
                        "action-owner-bind-v1",
                        null,
                        null,
                        null
                );
            }
        }""",
            ),
            (
                """List<ActionItemCandidate> ownerSanitized =
                sanitizeUnknownOwners(dated, participants, ctx.transcriptSegments(), stats);""",
                """List<ActionItemCandidate> ownerSanitized =
                sanitizeUnknownOwners(dated, participants, ctx.transcriptSegments(), stats);
        for (int __i = 0; __i < dated.size() && __i < ownerSanitized.size(); __i++) {
            ActionItemCandidate beforeOwner = dated.get(__i);
            ActionItemCandidate afterOwner = ownerSanitized.get(__i);
            if (!java.util.Objects.equals(beforeOwner.owner(), afterOwner.owner())) {
                LineageSupport.record(
                        LineageSupport.idOf("action", afterOwner.text(), afterOwner.evidenceSegmentIds()),
                        "ACTION_ITEM",
                        LineageStage.ACTION_CLAUSE_BINDING,
                        LineageOperation.UPDATE,
                        LineageReasonCode.ACTION_OWNER_BOUND,
                        List.of(),
                        null,
                        ItemLineageRecord.snapshot(beforeOwner.text(), beforeOwner.owner(), beforeOwner.relativeDate(),
                                beforeOwner.evidenceSegmentIds()),
                        ItemLineageRecord.snapshot(afterOwner.text(), afterOwner.owner(), afterOwner.relativeDate(),
                                afterOwner.evidenceSegmentIds()),
                        "action-owner-bind-v1",
                        null,
                        null,
                        null
                );
            }
        }""",
            ),
        ]
        for old, new in patterns:
            if old in t:
                t = t.replace(old, new, 1)
                break
        else:
            print(f"WARN: could not inject owner-binding lineage into {path}")

    # --- title backfill (A only; B has no titleBackfiller) ---
    if "titleBackfiller.backfill" in t and "ACTION_TITLE_BACKFILL" not in t:
        old = """List<ActionItemCandidate> titlesFilled =
                titleBackfiller.backfill(ownerSanitized, ctx.transcriptSegments());"""
        new = """List<ActionItemCandidate> titlesFilled =
                titleBackfiller.backfill(ownerSanitized, ctx.transcriptSegments());
        for (int __i = 0; __i < ownerSanitized.size() && __i < titlesFilled.size(); __i++) {
            ActionItemCandidate beforeTitle = ownerSanitized.get(__i);
            ActionItemCandidate afterTitle = titlesFilled.get(__i);
            if (!java.util.Objects.equals(beforeTitle.text(), afterTitle.text())) {
                LineageSupport.record(
                        LineageSupport.idOf("action", afterTitle.text(), afterTitle.evidenceSegmentIds()),
                        "ACTION_ITEM",
                        LineageStage.ACTION_TITLE_BACKFILL,
                        LineageOperation.UPDATE,
                        LineageReasonCode.ACTION_TITLE_BACKFILLED,
                        List.of(),
                        null,
                        ItemLineageRecord.snapshot(beforeTitle.text(), beforeTitle.owner(), beforeTitle.relativeDate(),
                                beforeTitle.evidenceSegmentIds()),
                        ItemLineageRecord.snapshot(afterTitle.text(), afterTitle.owner(), afterTitle.relativeDate(),
                                afterTitle.evidenceSegmentIds()),
                        "action-title-backfill-v1",
                        null,
                        null,
                        null
                );
            }
        }"""
        if old in t:
            t = t.replace(old, new, 1)
        else:
            print(f"WARN: could not inject title-backfill lineage into {path}")

    # --- dedup DROP via set difference ---
    if "ACTION_DEDUPLICATION" not in t:
        # After dedup.removed loop / warnings — insert before CommitmentOwnerBinder
        old = "flags.addAll(dedup.warnings());\n\n        CommitmentOwnerBinder.Result commitmentsBound ="
        new = """flags.addAll(dedup.warnings());
        {
            java.util.Set<String> keptKeys = new java.util.LinkedHashSet<>();
            for (ActionItemCandidate a : dedup.actions()) {
                keptKeys.add(LineageSupport.idOf("action", a.text(), a.evidenceSegmentIds()));
            }
            java.util.List<ActionItemCandidate> dedupInput =
                    titlesFilled != null ? List.copyOf(registerNormalized) : List.copyOf(ownerSanitized);
            // resolve input list for B (no titlesFilled/registerNormalized)
        }
        CommitmentOwnerBinder.Result commitmentsBound ="""
        # Simpler approach: always compute against ownerSanitized or registerNormalized if present
        insert = """
        {
            java.util.Set<String> keptKeys = new java.util.LinkedHashSet<>();
            for (ActionItemCandidate a : dedup.actions()) {
                keptKeys.add(a.text() + "|" + String.join(",", a.evidenceSegmentIds()));
            }
            java.util.List<ActionItemCandidate> __dedupIn;
            try {
                __dedupIn = registerNormalized;
            } catch (Throwable __ignore) {
                __dedupIn = ownerSanitized;
            }
            for (ActionItemCandidate a : __dedupIn) {
                String key = a.text() + "|" + String.join(",", a.evidenceSegmentIds());
                if (!keptKeys.contains(key)) {
                    LineageSupport.record(
                            LineageSupport.idOf("action", a.text(), a.evidenceSegmentIds()),
                            "ACTION_ITEM",
                            LineageStage.ACTION_DEDUPLICATION,
                            LineageOperation.DROP,
                            LineageReasonCode.ACTION_DEDUPLICATED,
                            List.of(),
                            null,
                            ItemLineageRecord.snapshot(a.text(), a.owner(), a.relativeDate(), a.evidenceSegmentIds()),
                            java.util.Map.of(),
                            "action-dedup-v1",
                            null,
                            null,
                            null
                    );
                }
            }
        }
"""
        # try/catch for registerNormalized won't compile in Java. Use presence check in Python.
        if "registerNormalized" in t:
            dedup_input = "registerNormalized"
        else:
            dedup_input = "ownerSanitized"
        insert = f"""
        {{
            java.util.Set<String> keptKeys = new java.util.LinkedHashSet<>();
            for (ActionItemCandidate a : dedup.actions()) {{
                keptKeys.add(a.text() + "|" + String.join(",", a.evidenceSegmentIds()));
            }}
            for (ActionItemCandidate a : {dedup_input}) {{
                String key = a.text() + "|" + String.join(",", a.evidenceSegmentIds());
                if (!keptKeys.contains(key)) {{
                    LineageSupport.record(
                            LineageSupport.idOf("action", a.text(), a.evidenceSegmentIds()),
                            "ACTION_ITEM",
                            LineageStage.ACTION_DEDUPLICATION,
                            LineageOperation.DROP,
                            LineageReasonCode.ACTION_DEDUPLICATED,
                            List.of(),
                            null,
                            ItemLineageRecord.snapshot(a.text(), a.owner(), a.relativeDate(), a.evidenceSegmentIds()),
                            java.util.Map.of(),
                            "action-dedup-v1",
                            null,
                            null,
                            null
                    );
                }}
            }}
        }}
"""
        needle = "flags.addAll(dedup.warnings());"
        if needle in t and "ACTION_DEDUPLICATION" not in t:
            t = t.replace(needle, needle + insert, 1)
        else:
            print(f"WARN: could not inject dedup lineage into {path}")

    path.write_text(t)


def patch_meeting_item_policy(path: Path) -> None:
    if not path.exists():
        return
    t = path.read_text()
    if "LineageSupport" in t and "MEETING_ITEM_POLICY" in t:
        return
    t = ensure_lineage_imports(t)
    # Convert `return switch` into assign + observe + return
    if "return switch (type)" in t:
        t = t.replace(
            "return switch (type) {",
            "PolicyAction action = switch (type) {",
            1,
        )
        # After closing of switch `};` that ends decide method — find first `        };\n    }` after switch
        # Insert observe before method end
        old_end = """            case ISSUE, RISK -> PolicyAction.KEEP;
        };
    }"""
        new_end = """            case ISSUE, RISK -> PolicyAction.KEEP;
        };
        observe(type, act, text, action);
        return action;
    }

    private static void observe(
            MeetingItemType type,
            MeetingSpeechAct act,
            String text,
            PolicyAction action
    ) {
        LineageSupport.record(
                LineageSupport.idOf("policy", text, List.of()),
                type.name(),
                LineageStage.MEETING_ITEM_POLICY,
                action == PolicyAction.DROP ? LineageOperation.DROP : LineageOperation.KEEP,
                action == PolicyAction.DROP ? LineageReasonCode.POLICY_DROP : LineageReasonCode.POLICY_KEEP,
                List.of(),
                null,
                ItemLineageRecord.snapshot(text, null, null, List.of()),
                ItemLineageRecord.snapshot(text, null, null, List.of()),
                "meeting-item-policy-v1",
                null,
                null,
                act == null ? null : act.name()
        );
    }"""
        if old_end in t:
            t = t.replace(old_end, new_end, 1)
        else:
            print(f"WARN: could not inject MeetingItemPolicy observe into {path}")
    path.write_text(t)


def patch_extraction_merger(path: Path) -> None:
    if not path.exists():
        return
    t = path.read_text()
    if "LineageStage.MERGE" in t:
        return
    t = ensure_lineage_imports(t)
    old = """for (ActionItemCandidate item : chunk.actionItems()) {
                String key = norm(item.text());
                actions.putIfAbsent(key, item);
            }"""
    # B/A may already have putIfAbsent without lineage
    if old not in t:
        # try variant with containsKey already (main)
        if "LineageStage.MERGE" in t:
            return
        print(f"WARN: ExtractionMerger pattern not found in {path}")
        path.write_text(t)
        return
    new = """for (ActionItemCandidate item : chunk.actionItems()) {
                String key = norm(item.text());
                if (actions.containsKey(key)) {
                    LineageSupport.record(
                            LineageSupport.idOf("action", item.text(), item.evidenceSegmentIds()),
                            "ACTION_ITEM",
                            LineageStage.MERGE,
                            LineageOperation.MERGE,
                            LineageReasonCode.MERGED_AS_DUPLICATE,
                            List.of(LineageSupport.idOf("action", actions.get(key).text(), actions.get(key).evidenceSegmentIds())),
                            null,
                            ItemLineageRecord.snapshot(item.text(), item.owner(), item.relativeDate(), item.evidenceSegmentIds()),
                            ItemLineageRecord.snapshot(actions.get(key).text(), actions.get(key).owner(),
                                    actions.get(key).relativeDate(), actions.get(key).evidenceSegmentIds()),
                            "extraction-merger-v1",
                            null,
                            null,
                            null
                    );
                }
                actions.putIfAbsent(key, item);
            }"""
    t = t.replace(old, new, 1)
    path.write_text(t)


def patch_json_repair(path: Path) -> None:
    if not path.exists():
        return
    t = path.read_text()
    if "observeRepair" in t:
        return
    t = ensure_lineage_imports(t)
    if "import java.util.Map;" not in t and "java.util.Map" not in t:
        t = ensure_import(t, "import java.util.Map;")
    if "import java.util.Objects;" not in t:
        t = ensure_import(t, "import java.util.Objects;")
    helper = '''
    private static void observeRepair(String before, String after) {
        if (before == null || after == null || before.equals(after)) {
            return;
        }
        LineageSupport.record(
                "json-repair-" + Integer.toHexString(Objects.hash(before.length(), after.length())),
                "EXTRACTION_JSON",
                LineageStage.JSON_REPAIR,
                LineageOperation.UPDATE,
                LineageReasonCode.JSON_REPAIRED,
                List.of(),
                null,
                Map.of("bytesBefore", before.length(), "bytesAfter", after.length()),
                Map.of("bytesBefore", before.length(), "bytesAfter", after.length()),
                "limited-json-repair-v1",
                null,
                null,
                null
        );
    }

'''
    if "public boolean needsRepair(String raw)" in t:
        t = t.replace(
            "public boolean needsRepair(String raw)",
            helper + "    public boolean needsRepair(String raw)",
            1,
        )
    # B/A LimitedJsonRepair uses method arg `raw` (not `original`)
    raw_name = "raw" if re.search(r"repair\([^)]*\braw\b", t) else "original"
    t = re.sub(
        r"return candidate;",
        f"observeRepair({raw_name}, candidate);\n            return candidate;",
        t,
        count=3,
    )
    path.write_text(t)


def patch_speech_act(path: Path) -> None:
    if not path.exists():
        return
    t = path.read_text()
    if "SPEECH_ACT_CLASSIFICATION" in t:
        return
    t = ensure_lineage_imports(t)
    observe_fn = """
    private static void observe(String text, SpeechActResult result) {
        LineageReasonCode reason = switch (result.speechAct()) {
            case STATUS_QUO -> LineageReasonCode.SPEECH_ACT_STATUS_QUO;
            case EXPLICIT_DECISION -> LineageReasonCode.SPEECH_ACT_EXPLICIT_DECISION;
            case PROPOSAL_CUE -> LineageReasonCode.SPEECH_ACT_PROPOSAL;
            default -> LineageReasonCode.SPEECH_ACT_UNKNOWN;
        };
        LineageSupport.record(
                LineageSupport.idOf("speech", text, List.of()),
                "SPEECH_ACT",
                LineageStage.SPEECH_ACT_CLASSIFICATION,
                LineageOperation.FLAG,
                reason,
                List.of(),
                null,
                ItemLineageRecord.snapshot(text, null, null, List.of()),
                ItemLineageRecord.snapshot(text, null, null, List.of()),
                "hybrid-speech-act-v1",
                null,
                null,
                null
        );
    }
"""
    # Wrap classify body: rename method and add observing facade.
    m = re.search(
        r"public SpeechActResult classify\(String text\) \{(?P<body>.*?)\n    \}\n\n    public DeterministicSpeechActMatcher deterministic\(\)",
        t,
        flags=re.S,
    )
    if not m:
        print(f"WARN: could not inject speech-act lineage into {path}")
        path.write_text(t)
        return
    body = m.group("body")
    wrapped = (
        "public SpeechActResult classify(String text) {\n"
        "        SpeechActResult result = classifyForLineage(text);\n"
        "        observe(text, result);\n"
        "        return result;\n"
        "    }\n\n"
        "    private SpeechActResult classifyForLineage(String text) {"
        + body
        + "\n    }\n"
        + observe_fn
        + "\n    public DeterministicSpeechActMatcher deterministic()"
    )
    t = t[: m.start()] + wrapped + t[m.end() :]
    path.write_text(t)


def patch_crosstype(path: Path) -> None:
    if not path.exists():
        return
    t = path.read_text()
    if "recordLineageObservability" in t:
        return
    t = ensure_lineage_imports(t)
    t = ensure_import(t, "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.ItemLineageRecorder;")
    t = ensure_import(t, "import java.time.Instant;")
    t = ensure_import(t, "import java.util.HashSet;")
    t = ensure_import(t, "import java.util.Map;")
    # Insert call before return new Outcome
    old = """return new Outcome(
                keptDecisions,
                List.copyOf(keptActions),
                List.copyOf(keptCommitments),
                List.copyOf(flags),
                actionsDropped,
                commitmentsDropped
        );
    }"""
    new = """Outcome outcome = new Outcome(
                keptDecisions,
                List.copyOf(keptActions),
                List.copyOf(keptCommitments),
                List.copyOf(flags),
                actionsDropped,
                commitmentsDropped
        );
        recordLineageObservability(actions, commitments, outcome);
        return outcome;
    }

    /** Side-effect only: never mutates outcome; failures are swallowed by the recorder. */
    private void recordLineageObservability(
            List<ActionItemCandidate> inputActions,
            List<CommitmentCandidate> inputCommitments,
            Outcome outcome
    ) {
        try {
            ItemLineageRecorder recorder = ItemLineageRecorder.current();
            if (!recorder.isEnabled()) {
                return;
            }
            Instant now = Instant.now();
            Set<String> keptActionKeys = new HashSet<>();
            for (ActionItemCandidate a : outcome.actions()) {
                keptActionKeys.add(identity.canonicalCore(a) + "|" + String.join(",", a.evidenceSegmentIds()));
                recorder.record(new ItemLineageRecord(
                        "action-" + Integer.toHexString(Objects.hash(a.text(), a.evidenceSegmentIds())),
                        "ACTION_ITEM",
                        LineageStage.CROSS_TYPE_RESOLUTION,
                        LineageOperation.KEEP,
                        LineageReasonCode.POLICY_KEEP,
                        List.of(),
                        ItemLineageRecord.snapshot(a.text(), a.owner(), a.relativeDate(), a.evidenceSegmentIds()),
                        ItemLineageRecord.snapshot(a.text(), a.owner(), a.relativeDate(), a.evidenceSegmentIds()),
                        "cross-type-existing-v1",
                        now,
                        null,
                        null,
                        null
                ));
            }
            for (ActionItemCandidate a : inputActions) {
                String key = identity.canonicalCore(a) + "|" + String.join(",", a.evidenceSegmentIds());
                if (keptActionKeys.contains(key)) {
                    continue;
                }
                recorder.record(new ItemLineageRecord(
                        "action-" + Integer.toHexString(Objects.hash(a.text(), a.evidenceSegmentIds())),
                        "ACTION_ITEM",
                        LineageStage.CROSS_TYPE_RESOLUTION,
                        LineageOperation.DROP,
                        LineageReasonCode.CROSS_TYPE_ACTION_SUBSUMED,
                        List.of(),
                        ItemLineageRecord.snapshot(a.text(), a.owner(), a.relativeDate(), a.evidenceSegmentIds()),
                        Map.of(),
                        "cross-type-existing-v1",
                        now,
                        null,
                        null,
                        null
                ));
            }
            Set<String> keptCommitmentKeys = new HashSet<>();
            for (CommitmentCandidate c : outcome.commitments()) {
                keptCommitmentKeys.add(identity.canonicalCore(c.text()) + "|" + String.join(",", c.evidenceSegmentIds()));
            }
            for (CommitmentCandidate c : inputCommitments) {
                String key = identity.canonicalCore(c.text()) + "|" + String.join(",", c.evidenceSegmentIds());
                if (keptCommitmentKeys.contains(key)) {
                    continue;
                }
                recorder.record(new ItemLineageRecord(
                        "commitment-" + Integer.toHexString(Objects.hash(c.text(), c.evidenceSegmentIds())),
                        "COMMITMENT",
                        LineageStage.CROSS_TYPE_RESOLUTION,
                        LineageOperation.DROP,
                        LineageReasonCode.CROSS_TYPE_COMMITMENT_SUBSUMED,
                        List.of(),
                        ItemLineageRecord.snapshot(c.text(), c.owner(), null, c.evidenceSegmentIds()),
                        Map.of(),
                        "cross-type-existing-v1",
                        now,
                        null,
                        null,
                        null
                ));
            }
        } catch (RuntimeException ignored) {
            // Observability must never break extraction.
        }
    }"""
    if old in t:
        t = t.replace(old, new, 1)
    else:
        print(f"WARN: could not inject CrossType lineage into {path}")
    path.write_text(t)


PERSIST_METHOD = '''
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


def patch_executor(path: Path) -> None:
    if not path.exists():
        return
    t = path.read_text()
    t = ensure_import(t, "import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.ItemLineageRecorder;")
    if "lineageRecordingEnabled" not in t:
        t = t.replace(
            "private final int parallelChunkLimit;",
            "private final int parallelChunkLimit;\n    private final boolean lineageRecordingEnabled;",
            1,
        )
        t = t.replace(
            "this.parallelChunkLimit = parallelChunkLimit;\n    }",
            "this.parallelChunkLimit = parallelChunkLimit;\n        this.lineageRecordingEnabled = false;\n    }",
            1,
        )
    old = "PipelineRunResult result = extractionPipeline.run("
    if old in t and "ItemLineageRecorder.install" not in t:
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
    if "private void persistItemLineageArtifact" not in t:
        t = t.replace(
            "private static boolean usesExtractionPipeline(",
            PERSIST_METHOD + "    private static boolean usesExtractionPipeline(",
            1,
        )
    path.write_text(t)


def patch_pipeline_props(path: Path) -> None:
    if not path.exists():
        return
    t = path.read_text()
    if "lineageRecordingEnabled" not in t:
        t = t.replace(
            "private String finalizationFailureMode;",
            "private String finalizationFailureMode;\n    private boolean lineageRecordingEnabled;",
            1,
        )
    if "isLineageRecordingEnabled" not in t:
        idx = t.rfind("}")
        if idx >= 0:
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
    patch_meeting_item_policy(
        root
        / "modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/domain/pipeline/filter/MeetingItemPolicy.java"
    )
    patch_extraction_merger(
        root
        / "modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/domain/pipeline/ExtractionMerger.java"
    )
    patch_json_repair(
        root
        / "modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/infrastructure/json/LimitedJsonRepair.java"
    )
    patch_speech_act(
        root
        / "modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/domain/pipeline/speechact/HybridSpeechActClassifier.java"
    )
    ct = (
        root
        / "modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/domain/pipeline/consistency/CrossTypeMeetingItemSubsumer.java"
    )
    if ct.exists():
        patch_crosstype(ct)
    else:
        print(f"INFO: CrossType absent on {args.label} (expected for B)")
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
