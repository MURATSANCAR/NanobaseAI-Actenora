package com.nanobaseai.actenora.transcript.infrastructure.storage;

import com.nanobaseai.actenora.sharedkernel.port.storage.AuthorizedUrl;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectMetadata;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectPutRequest;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorageException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory ObjectStorage for unit tests. Can simulate timeouts.
 */
public final class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, Stored> objects = new ConcurrentHashMap<>();
    private volatile boolean forceTimeout;

    public void forceTimeout(boolean forceTimeout) {
        this.forceTimeout = forceTimeout;
    }

    @Override
    public ObjectMetadata put(ObjectPutRequest request) {
        checkTimeout(request.key());
        if (request.immutable() && objects.containsKey(request.key())) {
            throw ObjectStorageException.alreadyExists(request.key());
        }
        byte[] bytes;
        try {
            bytes = request.content().readAllBytes();
        } catch (IOException e) {
            throw new ObjectStorageException("OBJECT_STORAGE_IO", "Failed to read put stream", e);
        }
        Instant now = Instant.now();
        Instant retentionUntil = null;
        String retention = request.userMetadata().get("retention-until");
        if (retention != null) {
            retentionUntil = Instant.parse(retention);
        }
        Stored stored = new Stored(bytes, request.contentType(), request.userMetadata(), now, retentionUntil);
        objects.put(request.key(), stored);
        return toMetadata(request.key(), stored);
    }

    @Override
    public InputStream get(String key) {
        checkTimeout(key);
        Stored stored = objects.get(key);
        if (stored == null) {
            throw ObjectStorageException.notFound(key);
        }
        return new ByteArrayInputStream(stored.bytes);
    }

    @Override
    public void delete(String key) {
        checkTimeout(key);
        objects.remove(key);
    }

    @Override
    public boolean exists(String key) {
        checkTimeout(key);
        return objects.containsKey(key);
    }

    @Override
    public Optional<ObjectMetadata> metadata(String key) {
        checkTimeout(key);
        Stored stored = objects.get(key);
        if (stored == null) {
            return Optional.empty();
        }
        return Optional.of(toMetadata(key, stored));
    }

    @Override
    public AuthorizedUrl generateAuthorizedUrl(String key, Duration ttl) {
        checkTimeout(key);
        if (!objects.containsKey(key)) {
            throw ObjectStorageException.notFound(key);
        }
        Instant expires = Instant.now().plus(ttl);
        URI url = URI.create("memory://object-storage/" + key + "?expires=" + expires.toEpochMilli());
        return new AuthorizedUrl(url, expires);
    }

    /**
     * Resolves a previously issued memory:// authorized URL, enforcing expiry.
     */
    public InputStream resolveAuthorizedUrl(AuthorizedUrl authorizedUrl, Instant now) {
        Objects.requireNonNull(authorizedUrl, "authorizedUrl");
        Objects.requireNonNull(now, "now");
        if (authorizedUrl.isExpired(now)) {
            throw new ObjectStorageException("SIGNED_URL_EXPIRED", "Authorized URL has expired");
        }
        URI url = authorizedUrl.url();
        if (!"memory".equals(url.getScheme())) {
            throw new ObjectStorageException("SIGNED_URL_INVALID", "Unsupported authorized URL scheme");
        }
        String path = url.getPath();
        if (path == null || !path.startsWith("/")) {
            throw new ObjectStorageException("SIGNED_URL_INVALID", "Malformed authorized URL");
        }
        String key = path.startsWith("/") ? path.substring(1) : path;
        return get(key);
    }

    public java.util.List<ObjectMetadata> listExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return objects.entrySet().stream()
                .filter(e -> e.getValue().retentionUntil != null && !e.getValue().retentionUntil.isAfter(now))
                .map(e -> toMetadata(e.getKey(), e.getValue()))
                .toList();
    }

    private void checkTimeout(String key) {
        if (forceTimeout) {
            throw ObjectStorageException.timeout(key, new java.util.concurrent.TimeoutException("simulated"));
        }
    }

    private static ObjectMetadata toMetadata(String key, Stored stored) {
        return new ObjectMetadata(
                key,
                stored.bytes.length,
                stored.contentType,
                Integer.toHexString(stored.bytes.length),
                stored.lastModified,
                stored.userMetadata,
                stored.retentionUntil);
    }

    private record Stored(
            byte[] bytes,
            String contentType,
            Map<String, String> userMetadata,
            Instant lastModified,
            Instant retentionUntil) {
    }
}
