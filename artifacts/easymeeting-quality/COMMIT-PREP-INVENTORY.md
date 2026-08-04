# Measurement infra commit prep (no commit created)

## Candidate single commit (code + docs + scripts; no run blobs)

- `modules/ai-processing/.../lineage/*`
- Lineage hooks: CrossType, MeetingItemPolicy, ActionPostProcessing, ExtractionMerger, LimitedJsonRepair, HybridSpeechActClassifier
- `AiPipelineProperties` + `application.yml` lineage flag
- Gold JSON + GoldStandupContractTest
- Scorer/analyzer scripts + Python unit tests
- `docs/reviews/EASYMEETING-QUALITY-EVALUATION-PROTOCOL-V0.1.md`
- Small manifests: `baseline-manifest.json`, `test-baseline-summary.json`, failure-matrix + root-cause summaries (JSON/MD)

## Keep out of git (external archive)

- `artifacts/easymeeting-quality/{B_HEAD_*,A_CAND_*}/run_*/final.note.json` and meta (10 LLM run blobs)
- Suggested: `.gitignore` entry `artifacts/easymeeting-quality/**/run_*/`
- Archive: `nanobase:/data/nanobaseai/actenora/eval/standup-v0.1/` (+ phase1b copies)

## Observability note

Existing 10 runs lack intermediate artifacts; stage chains are NOT_OBSERVABLE.
Lineage hooks apply to future runs when `ACTENORA_AI_PIPELINE_LINEAGE_RECORDING=true`.
