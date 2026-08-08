package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

/**
 * Production token budgets for the meeting-minutes pipeline on Qwen3.6-35B-A3B (CPU).
 *
 * <p>Server {@code --ctx-size} is 32_768 with {@code -np 1} (verified in prod), so a single
 * request may use the full window. Chunking and the per-chunk {@code assertFits} guard therefore
 * use {@link #OPERATIONAL_CTX_SIZE} (32_768). The earlier 16_384 clamp silently dropped chunks on
 * dense, many-short-segment transcripts: the rendered extraction prompt (per-segment UUID
 * evidence-ids + speaker-role labels, which scale with segment COUNT, not content size) overflowed
 * 16_384 even for a target-sized chunk, and the failed chunk — with its decisions — was discarded.
 *
 * <p>Critical distinction:
 * <ul>
 *   <li>{@code max_tokens} / stage max output = generation cap</li>
 *   <li>chunk target/max = transcript input slice size per LLM call</li>
 * </ul>
 */
public final class MeetingLlmBudgets {

    /** Operational context budget used for chunking. Matches the prod llama-server {@code -c 32768 -np 1}. */
    public static final int OPERATIONAL_CTX_SIZE = 32_768;

    /**
     * Context budget used by the transcript-grounded finalization step. The production
     * llama-server runs with {@code -c 65536} (model n_ctx_train is 262144, so this is safe),
     * so the finalizer feeds the whole transcript for far more meetings — long BİM-scale
     * meetings now stay on the higher-quality grounded path instead of degrading to FULL.
     */
    public static final int GROUNDED_CTX_SIZE = 65_536;

    /**
     * Transcript-token ceiling for the grounded finalization path. Grounded re-reads the WHOLE
     * transcript, so its wall-clock cost grows with transcript length — on CPU (~23 tok/s prompt
     * processing) a large meeting takes longer than any fixed timeout. Above this ceiling the
     * finalizer routes to candidate-grounded FULL, whose cost is bounded by the number of extracted
     * items (not transcript length) and therefore stays fast and reliable for ANY meeting duration.
     * Grounded stays reserved for short meetings where the full-transcript re-verify is cheap.
     */
    public static final int GROUNDED_MAX_TRANSCRIPT_TOKENS = 12_000;

    /** Preferred transcript tokens per extraction call. */
    public static final int TARGET_CHUNK_TOKENS = 3_500;

    /** Hard ceiling for a single transcript chunk. */
    public static final int MAX_CHUNK_TOKENS = 4_500;

    /** Overlap between adjacent chunks. */
    public static final int OVERLAP_TOKENS = 250;

    /**
     * Reserved for system prompt + extraction instructions + JSON schema + metadata.
     * Must stay aligned with real prompt footprint under ctx-size.
     */
    public static final int PROMPT_OVERHEAD_TOKENS = 2_500;

    /** Safety margin so prompt+chunk+output never saturates KV cache. */
    public static final int SAFETY_MARGIN_TOKENS = 1_000;

    /**
     * CHUNK_EXTRACTION generation cap. Dense, evidence-rich 15-minute transcripts can exceed
     * 4k JSON tokens; 6k still keeps prompt + target chunk + output inside the 16k operational
     * context while avoiding partial-JSON recovery.
     */
    public static final int EXTRACTION_MAX_TOKENS = 6_144;

    /** MEETING_TRIAGE generation cap (tiny JSON classifier). */
    public static final int TRIAGE_MAX_TOKENS = 512;

    /** CANDIDATE_MERGE generation cap. */
    public static final int MERGE_MAX_TOKENS = 2_048;

    /**
     * FINAL_NOTE / final minutes generation cap.
     * 2048 and 4096 both truncate dense TR minutes JSON on eval-1h (finish_reason=length → SYNTHESIS_FALLBACK).
     */
    public static final int FINAL_MAX_TOKENS = 8_192;

    /** Evidence-audit generation cap — 800 truncates multi-item audit arrays. */
    public static final int AUDIT_MAX_TOKENS = 2_048;

    /** Descriptor / fallback ceiling when a call does not specify a stage budget. */
    public static final int DEFAULT_MAX_TOKENS = FINAL_MAX_TOKENS;

    private MeetingLlmBudgets() {
    }

    /**
     * Clamp a registry context window to the operational llama-server ctx-size (32k).
     * Registry may advertise a larger native window; the running server uses 32k with -np 1.
     */
    public static int operationalContextWindow(int registryOrNativeContextWindow) {
        if (registryOrNativeContextWindow <= 0) {
            return OPERATIONAL_CTX_SIZE;
        }
        return Math.min(registryOrNativeContextWindow, OPERATIONAL_CTX_SIZE);
    }

    /**
     * Clamp a registry max-output to the meeting-pipeline ceiling.
     */
    public static int operationalMaxOutput(int registryOrNativeMaxOutput) {
        if (registryOrNativeMaxOutput <= 0) {
            return DEFAULT_MAX_TOKENS;
        }
        return Math.min(registryOrNativeMaxOutput, DEFAULT_MAX_TOKENS);
    }

    /** Turkish + UUID-dense prompts tokenize ~1.5x above the len/4 estimate; correct before budgeting. */
    public static final double PROMPT_TOKEN_DENSITY_FACTOR = 1.5d;

    /** Floor so a finalization call always generates a usable note even when the prompt is huge. */
    public static final int MIN_FINALIZATION_OUTPUT_TOKENS = 1_536;

    /**
     * Largest generation cap that still fits {@code contextWindow} after a (density-corrected)
     * prompt, clamped to {@code ceilingMaxOutput} and floored at {@link #MIN_FINALIZATION_OUTPUT_TOKENS}.
     * Prevents the finalization request from exceeding llama's context (HTTP 400) for large meetings —
     * the failure mode where a job "succeeds" but persists no note.
     */
    public static int fittingMaxOutput(int promptTokensEstimate, int contextWindow, int ceilingMaxOutput) {
        int correctedPrompt = (int) Math.ceil(Math.max(0, promptTokensEstimate) * PROMPT_TOKEN_DENSITY_FACTOR);
        int available = contextWindow - correctedPrompt - SAFETY_MARGIN_TOKENS;
        return Math.max(MIN_FINALIZATION_OUTPUT_TOKENS, Math.min(ceilingMaxOutput, available));
    }
}
