package com.nanobaseai.actenora.transcript.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.domain.parsing.VttParseResult;
import com.nanobaseai.actenora.transcript.domain.parsing.VttParser;

import java.util.List;

/**
 * Structural VTT cue extractor used by ingest/reparse.
 * Delegates to {@link VttParser} (FAZ 9) for deterministic parse semantics.
 */
public final class StructuralVttParser {

    private StructuralVttParser() {
    }

    public static List<TranscriptSegment> parse(
            TenantId tenantId,
            TranscriptId transcriptId,
            byte[] rawBytes) {
        return parseDetailed(tenantId, transcriptId, rawBytes).segments();
    }

    public static VttParseResult parseDetailed(
            TenantId tenantId,
            TranscriptId transcriptId,
            byte[] rawBytes) {
        return VttParser.parse(tenantId, transcriptId, rawBytes);
    }
}
