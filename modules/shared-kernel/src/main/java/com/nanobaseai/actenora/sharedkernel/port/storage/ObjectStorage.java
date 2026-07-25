package com.nanobaseai.actenora.sharedkernel.port.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

/**
 * Object-storage port (MinIO-compatible S3). Domain modules depend on this
 * interface only — never on vendor SDKs.
 */
public interface ObjectStorage {

    /**
     * Store bytes at {@code key}. Implementations must refuse overwrite when
     * {@link ObjectPutRequest#immutable()} is true and the key already exists.
     */
    ObjectMetadata put(ObjectPutRequest request);

    InputStream get(String key);

    void delete(String key);

    boolean exists(String key);

    Optional<ObjectMetadata> metadata(String key);

    /**
     * Time-limited URL for authorized download. Caller must already have
     * performed tenant/authorization checks.
     */
    AuthorizedUrl generateAuthorizedUrl(String key, Duration ttl);
}
