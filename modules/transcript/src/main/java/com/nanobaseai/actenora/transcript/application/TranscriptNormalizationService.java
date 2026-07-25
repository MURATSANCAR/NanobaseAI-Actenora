package com.nanobaseai.actenora.transcript.application;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectPutRequest;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.application.port.in.NormalizeTranscriptCommand;
import com.nanobaseai.actenora.transcript.application.port.in.ParseTranscriptCommand;
import com.nanobaseai.actenora.transcript.application.port.out.NormalizationRunRepository;
import com.nanobaseai.actenora.transcript.application.port.out.TenantDictionaryRepository;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptRepository;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.domain.StructuralVttParser;
import com.nanobaseai.actenora.transcript.domain.Transcript;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;
import com.nanobaseai.actenora.transcript.domain.dictionary.TenantDictionary;
import com.nanobaseai.actenora.transcript.domain.event.TranscriptDomainEvents;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationRun;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationVersion;
import com.nanobaseai.actenora.transcript.domain.normalization.TranscriptNormalizer;
import com.nanobaseai.actenora.transcript.domain.parsing.VttParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FAZ 9 parse + normalize use cases. Never logs transcript content.
 */
public class TranscriptNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptNormalizationService.class);

    private final TranscriptRepository transcriptRepository;
    private final TranscriptSegmentRepository segmentRepository;
    private final TenantDictionaryRepository dictionaryRepository;
    private final NormalizationRunRepository normalizationRunRepository;
    private final ObjectStorage objectStorage;
    private final InstantClock clock;

    public TranscriptNormalizationService(
            TranscriptRepository transcriptRepository,
            TranscriptSegmentRepository segmentRepository,
            TenantDictionaryRepository dictionaryRepository,
            NormalizationRunRepository normalizationRunRepository,
            ObjectStorage objectStorage,
            InstantClock clock) {
        this.transcriptRepository = transcriptRepository;
        this.segmentRepository = segmentRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.normalizationRunRepository = normalizationRunRepository;
        this.objectStorage = objectStorage;
        this.clock = clock;
    }

    public ParseResult parse(ParseTranscriptCommand command) {
        Transcript transcript = requireOwned(command.tenantId(), command.transcriptId());
        Instant now = clock.now();

        byte[] raw = readRaw(transcript);
        try {
            VttParseResult parsed = StructuralVttParser.parseDetailed(
                    transcript.tenantId(), transcript.id(), raw);
            segmentRepository.replaceAll(command.tenantId(), transcript.id(), parsed.segments());
            transcript.markParsed(now);
            transcriptRepository.save(transcript);

            TranscriptDomainEvents.TranscriptParsed event =
                    TranscriptDomainEvents.TranscriptParsed.of(
                            transcript.tenantId(),
                            transcript.id(),
                            parsed.segments().size(),
                            parsed.issues().size(),
                            now);

            log.info(
                    "Transcript parsed transcriptId={} tenantId={} segmentCount={} issueCount={}",
                    transcript.id().value(),
                    command.tenantId().value(),
                    parsed.segments().size(),
                    parsed.issues().size());

            return new ParseResult(transcript, parsed, List.of(event));
        } catch (TranscriptDomainException e) {
            transcript.markFailed(now);
            transcriptRepository.save(transcript);
            throw e;
        }
    }

    public NormalizeResult normalize(NormalizeTranscriptCommand command) {
        Transcript transcript = requireOwned(command.tenantId(), command.transcriptId());
        TenantDictionary dictionary = resolveDictionary(command);
        String normalizationVersion = NormalizationVersion.compose(dictionary.revision());

        Optional<NormalizationRun> existing = normalizationRunRepository.findIdempotent(
                command.tenantId(), command.transcriptId(), normalizationVersion);
        if (existing.isPresent()) {
            log.info(
                    "Normalization idempotent hit transcriptId={} tenantId={} version={} runId={}",
                    command.transcriptId().value(),
                    command.tenantId().value(),
                    normalizationVersion,
                    existing.get().id());
            return new NormalizeResult(transcript, existing.get(), List.of(), true);
        }

        Instant now = clock.now();
        transcript.markPendingNormalize(now);
        transcriptRepository.save(transcript);

        NormalizationRun run = NormalizationRun.request(
                command.tenantId(),
                command.transcriptId(),
                normalizationVersion,
                dictionary.revision(),
                now);
        List<Object> events = new ArrayList<>(run.pullDomainEvents());

        List<TranscriptSegment> segments = segmentRepository.findByTranscript(
                command.tenantId(), command.transcriptId());
        if (segments.isEmpty()) {
            run.markFailed(
                    "NO_SEGMENTS",
                    "Transcript has no parsed segments to normalize",
                    List.of(),
                    now);
            normalizationRunRepository.save(run);
            transcript.markFailed(now);
            transcriptRepository.save(transcript);
            events.addAll(run.pullDomainEvents());
            return new NormalizeResult(transcript, run, events, false);
        }

        try {
            // Re-run parse issues are not persisted separately; normalize from stored segments.
            TranscriptNormalizer.NormalizationOutcome outcome = TranscriptNormalizer.normalize(
                    segments, List.of(), dictionary);

            run.markSucceeded(
                    outcome.segments(),
                    outcome.speakerResolutions(),
                    outcome.issues(),
                    outcome.metrics(),
                    outcome.normalizedTranscriptHash(),
                    now);
            normalizationRunRepository.save(run);

            byte[] payload = CanonicalNormalizedPayload.toBytes(run);
            String key = transcript.normalizedStorageKey().orElseThrow();
            Map<String, String> metadata = new HashMap<>();
            metadata.put("tenant-id", command.tenantId().value().toString());
            metadata.put("transcript-id", transcript.id().value().toString());
            metadata.put("normalization-version", normalizationVersion);
            metadata.put("normalized-hash-sha256", outcome.normalizedTranscriptHash().sha256Hex());
            metadata.put("immutable", "true");
            objectStorage.put(ObjectPutRequest.builder()
                    .key(key)
                    .content(new ByteArrayInputStream(payload))
                    .contentLength(payload.length)
                    .contentType("application/json")
                    .metadata(metadata)
                    .immutable(true)
                    .build());

            transcript.markNormalized(now);
            transcriptRepository.save(transcript);
            events.addAll(run.pullDomainEvents());

            log.info(
                    "Transcript normalized transcriptId={} tenantId={} version={} hash={} segmentCount={}",
                    transcript.id().value(),
                    command.tenantId().value(),
                    normalizationVersion,
                    outcome.normalizedTranscriptHash().sha256Hex(),
                    outcome.segments().size());

            return new NormalizeResult(transcript, run, events, false);
        } catch (RuntimeException e) {
            run.markFailed(
                    "NORMALIZE_FAILED",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    List.of(),
                    now);
            normalizationRunRepository.save(run);
            transcript.markFailed(now);
            transcriptRepository.save(transcript);
            events.addAll(run.pullDomainEvents());
            throw e;
        }
    }

    private TenantDictionary resolveDictionary(NormalizeTranscriptCommand command) {
        if (command.dictionaryIdOptional().isPresent()) {
            return dictionaryRepository
                    .findById(command.tenantId(), command.dictionaryId())
                    .orElseThrow(() -> new TranscriptDomainException(
                            "DICTIONARY_NOT_FOUND", "Tenant dictionary not found"));
        }
        return dictionaryRepository
                .findActiveByTenant(command.tenantId())
                .orElseThrow(() -> new TranscriptDomainException(
                        "DICTIONARY_NOT_FOUND", "No active tenant dictionary"));
    }

    private byte[] readRaw(Transcript transcript) {
        try (InputStream in = objectStorage.get(transcript.rawStorageKey())) {
            return in.readAllBytes();
        } catch (TranscriptDomainException e) {
            throw e;
        } catch (Exception e) {
            throw new TranscriptDomainException("PARSE_READ_FAILED", "Failed to read raw transcript");
        }
    }

    private Transcript requireOwned(TenantId tenantId, TranscriptId id) {
        return transcriptRepository.findById(tenantId, id)
                .orElseThrow(() -> new TranscriptDomainException(
                        "TRANSCRIPT_NOT_FOUND",
                        "Transcript not found for tenant"));
    }

    public record ParseResult(
            Transcript transcript,
            VttParseResult parseResult,
            List<Object> domainEvents
    ) {
    }

    public record NormalizeResult(
            Transcript transcript,
            NormalizationRun run,
            List<Object> domainEvents,
            boolean idempotentHit
    ) {
    }

    /**
     * Deterministic JSON-ish payload for normalized storage (no Jackson in domain).
     */
    static final class CanonicalNormalizedPayload {
        private CanonicalNormalizedPayload() {
        }

        static byte[] toBytes(NormalizationRun run) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"normalizationVersion\":\"")
                    .append(escape(run.normalizationVersion()))
                    .append("\",\"normalizedTranscriptHash\":\"")
                    .append(run.normalizedTranscriptHash().map(h -> h.sha256Hex()).orElse(""))
                    .append("\",\"segments\":[");
            for (int i = 0; i < run.segments().size(); i++) {
                var s = run.segments().get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('{')
                        .append("\"originalSegmentId\":\"").append(s.originalSegmentId()).append("\",")
                        .append("\"sequence\":").append(s.sequence()).append(',')
                        .append("\"startOffsetMs\":").append(s.startOffsetMs()).append(',')
                        .append("\"endOffsetMs\":").append(s.endOffsetMs()).append(',')
                        .append("\"speakerDisplayName\":")
                        .append(jsonString(s.speakerDisplayName().orElse(null))).append(',')
                        .append("\"originalContent\":").append(jsonString(s.originalContent())).append(',')
                        .append("\"normalizedContent\":").append(jsonString(s.normalizedContent()))
                        .append('}');
            }
            sb.append("]}");
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        private static String jsonString(String value) {
            if (value == null) {
                return "null";
            }
            return "\"" + escape(value) + "\"";
        }

        private static String escape(String value) {
            return value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}
