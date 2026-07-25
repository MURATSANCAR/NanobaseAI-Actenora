package com.nanobaseai.actenora.template.infrastructure.storage;

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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory ObjectStorage for unit tests. Can simulate MinIO timeouts. */
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
        Stored stored = new Stored(bytes, request.contentType(), request.userMetadata(), now);
        objects.put(request.key(), stored);
        return new ObjectMetadata(
                request.key(), bytes.length, request.contentType(),
                Integer.toHexString(bytes.length), now, request.userMetadata(), null);
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
        return Optional.of(new ObjectMetadata(
                key, stored.bytes.length, stored.contentType,
                Integer.toHexString(stored.bytes.length), stored.lastModified, stored.userMetadata, null));
    }

    @Override
    public AuthorizedUrl generateAuthorizedUrl(String key, Duration ttl) {
        checkTimeout(key);
        if (!objects.containsKey(key)) {
            throw ObjectStorageException.notFound(key);
        }
        Instant expires = Instant.now().plus(ttl);
        return new AuthorizedUrl(URI.create("memory://object-storage/" + key + "?expires=" + expires), expires);
    }

    private void checkTimeout(String key) {
        if (forceTimeout) {
            throw ObjectStorageException.timeout(key, new java.util.concurrent.TimeoutException("simulated"));
        }
    }

    private record Stored(byte[] bytes, String contentType, Map<String, String> userMetadata, Instant lastModified) {
    }
}
