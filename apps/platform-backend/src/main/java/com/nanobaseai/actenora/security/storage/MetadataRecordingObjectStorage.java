package com.nanobaseai.actenora.security.storage;

import com.nanobaseai.actenora.meetingintelligence.application.port.ArtifactMetadataStorePort;
import com.nanobaseai.actenora.meetingintelligence.domain.artifact.ArtifactKind;
import com.nanobaseai.actenora.meetingintelligence.domain.artifact.ArtifactMetadata;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.AuthorizedUrl;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectMetadata;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectPutRequest;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decorates {@link ObjectStorage#put} to register tenant-scoped artifact metadata.
 * Metadata write failures must not roll back a successful object put.
 */
public final class MetadataRecordingObjectStorage implements ObjectStorage {

    private static final Pattern TENANT = Pattern.compile("^tenants/([0-9a-fA-F-]{36})/");
    private static final Pattern OCCURRENCE = Pattern.compile(
            "/(?:meetings|transcripts)/([0-9a-fA-F-]{36})(?:/|$)");
    private static final Pattern NOTE = Pattern.compile("/notes/([0-9a-fA-F-]{36})(?:/|$)");

    private final ObjectStorage delegate;
    private final ArtifactMetadataStorePort metadataStore;
    private final Clock clock;

    public MetadataRecordingObjectStorage(
            ObjectStorage delegate,
            ArtifactMetadataStorePort metadataStore,
            Clock clock
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.metadataStore = Objects.requireNonNull(metadataStore, "metadataStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ObjectMetadata put(ObjectPutRequest request) {
        ObjectMetadata stored = delegate.put(request);
        try {
            register(request, stored);
        } catch (RuntimeException ignored) {
            // object bytes are source of truth; metadata is best-effort registry
        }
        return stored;
    }

    @Override
    public InputStream get(String key) {
        return delegate.get(key);
    }

    @Override
    public void delete(String key) {
        delegate.delete(key);
    }

    @Override
    public boolean exists(String key) {
        return delegate.exists(key);
    }

    @Override
    public Optional<ObjectMetadata> metadata(String key) {
        return delegate.metadata(key);
    }

    @Override
    public AuthorizedUrl generateAuthorizedUrl(String key, Duration ttl) {
        return delegate.generateAuthorizedUrl(key, ttl);
    }

    private void register(ObjectPutRequest request, ObjectMetadata stored) {
        Optional<UUID> tenantUuid = matchUuid(TENANT, request.key());
        if (tenantUuid.isEmpty()) {
            return;
        }
        TenantId tenantId = TenantId.of(tenantUuid.get());
        String checksum = request.userMetadata().get("content-hash-sha256");
        if (checksum == null) {
            checksum = stored.etag();
        }
        metadataStore.save(new ArtifactMetadata(
                UUID.randomUUID(),
                tenantId,
                matchUuid(OCCURRENCE, request.key()),
                matchUuid(NOTE, request.key()),
                Optional.empty(),
                inferKind(request.key()),
                request.key(),
                request.contentType(),
                Optional.of(stored.sizeBytes()),
                Optional.ofNullable(checksum),
                clock.instant()
        ));
    }

    static ArtifactKind inferKind(String key) {
        if (key.endsWith("/raw.vtt")) {
            return ArtifactKind.TRANSCRIPT_RAW;
        }
        if (key.contains("/normalized/") || key.endsWith("/normalized.json")) {
            return ArtifactKind.TRANSCRIPT_NORMALIZED;
        }
        if (key.endsWith("/draft.json")) {
            return ArtifactKind.NOTE_DRAFT;
        }
        if (key.endsWith("/approved.json")) {
            return ArtifactKind.NOTE_APPROVED;
        }
        if (key.endsWith("/bundle.json")) {
            return ArtifactKind.EXTRACTION_BUNDLE;
        }
        return ArtifactKind.OTHER;
    }

    private static Optional<UUID> matchUuid(Pattern pattern, String key) {
        Matcher matcher = pattern.matcher(key);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(matcher.group(1)));
    }
}
