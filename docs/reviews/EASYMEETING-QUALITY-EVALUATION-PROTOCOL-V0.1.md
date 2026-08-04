# EasyMeeting Quality Evaluation Protocol v0.1

Status: Phase 0 baseline freeze + Phase 1 gold scorer / item-level lineage.

## Purpose

Measure quality of meeting analysis **without changing production extraction behavior**.

- Freeze control **B** and candidate **A** baselines.
- Score runs against Gold Standard v0.1 for `01_15dk_daily_standup.vtt`.
- Emit item-level lineage for observability (feature-flagged, fail-open).
- Decide champion eligibility only when A is not worse on critical recall and is better on duplicates.

## B / A baselines

| Role | Name | Commit |
|------|------|--------|
| Control (B) | `B_HEAD_f9c699f` | `f9c699f` (`f9c699f4753d7017d64aede84c6ee7da056a5f66`) |
| Candidate (A) | `A_CAND_472172a` | `472172a` (`472172a035da047189b292f7f6ee677c115963ad`) |

Manifest: `artifacts/easymeeting-quality/baseline-manifest.json`

## Gold fixture

- VTT: `modules/ai-processing/src/test/resources/aiprocessing/eval/01_15dk_daily_standup.vtt`
- Gold: `modules/ai-processing/src/test/resources/aiprocessing/eval/gold/01_15dk_daily_standup.gold.v1.json`
- Artifact copy: `artifacts/easymeeting-quality/gold/01_15dk_daily_standup.gold.v1.json`

Required counts: decisions 2, action items 7, risks 2, important facts 2, open questions 12.

## Config values (frozen)

| Key | Value |
|-----|-------|
| Pipeline mode | `staged` (default `ACTENORA_AI_PIPELINE_MODE`) |
| Finalization | `editorial` |
| Gate threshold | `4.5` |
| Chunk target / overlap tokens | `3500` / `250` |
| Extraction max tokens | `6144` |
| Temperature / Top P / Top K | `0.1` / `0.85` / `20` |

Served-model IDs are read from environment and written into the baseline manifest (never secrets/tokens).

## Artifact layout

```
artifacts/easymeeting-quality/
├── baseline-manifest.json
├── test-baseline-summary.json
├── gold/
├── B_HEAD_f9c699f/{run_01..run_05,stability.json}
├── A_CAND_472172a/{run_01..run_05,stability.json}
└── B_vs_A_comparison.json
```

Per-run expected files: `run-manifest.json`, `final-note.json` / `final.note.json`, `score.json`, plus optional intermediate artifacts. Missing intermediates are marked `NOT_AVAILABLE` in the run manifest — never fabricated.

Real LLM runs are produced on the eval host under `/data/nanobaseai/actenora/eval/standup-v0.1/` and imported locally.

## Scorer metrics

Deterministic Python scorer: `scripts/score-easymeeting-gold.py`

Inputs: gold JSON + `final.note.json` (+ optional `lineage.jsonl`).

Outputs: `score.json` with decision/action/risk/OQ/fact PRF, owner/date errors, compound-split accuracy, status-quo FP, cross-type duplicate rate, overall score, critical gate pass.

Matching: normalized Turkish text + Jaccard token similarity + owner/date context. Thresholds live in `matchConfig` inside each `score.json`. No LLM in the scorer.

## Root-cause reason codes

`GATE_FALSE_NEGATIVE`, `LLM_MISSED_ITEM`, `LLM_HALLUCINATION`, `JSON_REPAIR_ITEM_LOSS`, `SCHEMA_REJECTION`, `GROUNDING_FALSE_DROP`, `POST_FILTER_FALSE_DROP`, `CROSS_TYPE_FALSE_SUPPRESSION`, `CROSS_TYPE_DUPLICATE`, `COMPOUND_ACTION_NOT_SPLIT`, `OWNER_HALLUCINATION`, `OWNER_MISBINDING`, `DATE_HALLUCINATION`, `DATE_CROSSOVER`, `EVIDENCE_MISBINDING`, `STATUS_QUO_FALSE_POSITIVE`, `CLOSING_META_LEAKAGE`, `FINAL_SUMMARY_DISTORTION`, `UNCLASSIFIED_MISS`.

Only emit a root cause when evidence supports it; otherwise `UNCLASSIFIED_MISS`.

## Lineage model

Package: `com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage`

- Stages: `LLM_RAW` … `FINAL_NOTE_MAPPING` (see `LineageStage`)
- Operations: `CREATE`, `KEEP`, `UPDATE`, `MERGE`, `DROP`, `REJECT`, `FLAG`, `MAP`
- Config: `actenora.ai.pipeline.lineage-recording-enabled` / `ACTENORA_AI_PIPELINE_LINEAGE_RECORDING` (default **false** in production)
- Persistence: thread-local recorder → artifact `lineage.jsonl` preferred; no DB migration required for Phase 1
- Instrumented in Phase 1: `CROSS_TYPE_RESOLUTION` (KEEP/DROP observability only)
- Fail-open: lineage errors must never fail extraction

## Stability measurement

After N completed runs, write `stability.json` with gate pass rate and metric mean/min/max/stddev.

## Champion eligibility

A is champion-eligible only if **all** hold:

- critical decision recall ≥ B
- action recall ≥ B
- hallucinated decisions = 0
- owner/date hallucination = 0
- status-quo FP = 0
- critical gate pass rate ≥ 0.95
- cross-type duplicate rate **strictly less than** B
- truncation/fallback counts ≤ B (when metrics available)

Duplicate reduction cannot compensate for any recall drop.

## Known limitations

- Intermediate pipeline artifacts (`raw-extractions`, `gate-decisions`, full `lineage.jsonl` dumps) are not yet emitted by the production path; marked `NOT_AVAILABLE`.
- Only cross-type stage emits lineage events so far.
- Scorer evidence coverage / date accuracy may be `null` until validated-bundle artifacts exist.
- Hallucinated decision count is a proxy (excess decisions) until forbidden-cue grounding is fully wired from lineage.
- Local Mac cannot reliably finish 5× LLM runs; campaign must run on the eval host.

## Phase 2 entry criteria

Do **not** start typed relation resolver / quality behavior changes until:

1. Gold JSON validated by contract tests
2. Staged module tests green
3. B and A each have 5 completed real LLM runs
4. Each run has an artifact set (with honest `NOT_AVAILABLE` markers)
5. Final decisions/actions are traceable via lineage or explicit root-cause
6. Scorer is deterministic
7. Status-quo FP, owner/date crossover, compound split, and critical gate pass rate are automated
8. `B_vs_A_comparison.json` exists

## Quality principle

If a change reduces filler/duplicates while lowering critical decision or action recall by any amount, it cannot be champion.
