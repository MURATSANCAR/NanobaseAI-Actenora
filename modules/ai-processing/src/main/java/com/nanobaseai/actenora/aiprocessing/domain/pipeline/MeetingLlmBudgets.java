package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

/**
 * Production token budgets for the meeting-minutes pipeline on Qwen3.6-35B-A3B (CPU).
 *
 * <p>Server {@code --ctx-size} is typically 32_768. Pipeline chunking still uses
 * {@link #OPERATIONAL_CTX_SIZE} (16_384) so prompt+chunk+output stay well under KV.
 *
 * <p>Critical distinction:
 * <ul>
 *   <li>{@code max_tokens} / stage max output = generation cap</li>
 *   <li>chunk target/max = transcript input slice size per LLM call</li>
 * </ul>
 */
public final class MeetingLlmBudgets {

    /** Operational context budget used for chunking (server may advertise 32k). */
    public static final int OPERATIONAL_CTX_SIZE = 16_384;

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
     * Clamp a registry context window to the operational llama-server ctx-size.
     * Registry may advertise the model native 32k; the running server uses 16k.
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
}
