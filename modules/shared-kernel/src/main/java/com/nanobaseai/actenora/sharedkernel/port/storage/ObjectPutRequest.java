package com.nanobaseai.actenora.sharedkernel.port.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Request to store an object. User content must never be logged by callers.
 */
public final class ObjectPutRequest {

    private final String key;
    private final InputStream content;
    private final long contentLength;
    private final String contentType;
    private final Map<String, String> userMetadata;
    private final boolean immutable;

    private ObjectPutRequest(Builder builder) {
        this.key = Objects.requireNonNull(builder.key, "key");
        this.content = Objects.requireNonNull(builder.content, "content");
        this.contentLength = builder.contentLength;
        this.contentType = Objects.requireNonNull(builder.contentType, "contentType");
        this.userMetadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.userMetadata));
        this.immutable = builder.immutable;
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must be >= 0");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ObjectPutRequest ofBytes(String key, byte[] bytes, String contentType) {
        return builder()
                .key(key)
                .content(new ByteArrayInputStream(bytes))
                .contentLength(bytes.length)
                .contentType(contentType)
                .build();
    }

    public String key() {
        return key;
    }

    public InputStream content() {
        return content;
    }

    public long contentLength() {
        return contentLength;
    }

    public String contentType() {
        return contentType;
    }

    public Map<String, String> userMetadata() {
        return userMetadata;
    }

    public boolean immutable() {
        return immutable;
    }

    public static final class Builder {
        private String key;
        private InputStream content;
        private long contentLength = -1;
        private String contentType = "application/octet-stream";
        private final Map<String, String> userMetadata = new LinkedHashMap<>();
        private boolean immutable;

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder content(InputStream content) {
            this.content = content;
            return this;
        }

        public Builder contentLength(long contentLength) {
            this.contentLength = contentLength;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder metadata(String name, String value) {
            this.userMetadata.put(name, value);
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.userMetadata.putAll(metadata);
            return this;
        }

        public Builder immutable(boolean immutable) {
            this.immutable = immutable;
            return this;
        }

        public ObjectPutRequest build() {
            return new ObjectPutRequest(this);
        }
    }
}
