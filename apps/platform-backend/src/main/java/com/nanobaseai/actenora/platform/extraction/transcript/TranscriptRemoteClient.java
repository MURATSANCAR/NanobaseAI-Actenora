package com.nanobaseai.actenora.platform.extraction.transcript;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP client to extracted transcript-worker with connect/read timeouts and retry.
 * No distributed transaction — request/response only.
 */
public final class TranscriptRemoteClient {

    public static final String TENANT_HEADER = "X-Actenora-Tenant-Id";

    private final TranscriptRemoteProperties properties;
    private final HttpClient httpClient;
    private final AtomicInteger attemptCounter;

    public TranscriptRemoteClient(TranscriptRemoteProperties properties) {
        this(properties, null);
    }

    /**
     * @param attemptCounter optional counter for tests (incremented once per HTTP attempt).
     */
    public TranscriptRemoteClient(TranscriptRemoteProperties properties, AtomicInteger attemptCounter) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.attemptCounter = attemptCounter;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    public RemoteResponse upload(
            UUID tenantId,
            UUID meetingOccurrenceId,
            String filename,
            String contentType,
            byte[] content,
            String language,
            Integer retentionPolicyDays) {
        StringBuilder url = new StringBuilder(properties.getBaseUrl())
                .append("/api/v1/transcripts/upload")
                .append("?meetingOccurrenceId=").append(meetingOccurrenceId);
        if (language != null && !language.isBlank()) {
            url.append("&language=").append(java.net.URLEncoder.encode(language, StandardCharsets.UTF_8));
        }
        if (retentionPolicyDays != null) {
            url.append("&retentionPolicyDays=").append(retentionPolicyDays);
        }

        String boundary = "actenora-" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, filename, contentType, content);

        return executeWithRetry(() -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(properties.getReadTimeout())
                    .header(TENANT_HEADER, tenantId.toString())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            return send(request);
        });
    }

    public RemoteResponse postJson(String path, UUID tenantId, String query) {
        String url = properties.getBaseUrl() + path + (query == null || query.isBlank() ? "" : "?" + query);
        return executeWithRetry(() -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(properties.getReadTimeout())
                    .header(TENANT_HEADER, tenantId.toString())
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            return send(request);
        });
    }

    private RemoteResponse send(HttpRequest request) {
        if (attemptCounter != null) {
            attemptCounter.incrementAndGet();
        }
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return new RemoteResponse(
                    response.statusCode(),
                    response.body(),
                    response.headers().firstValue("Content-Type").orElse("application/json"));
        } catch (IOException e) {
            throw new TransientRemoteException("Network I/O failure talking to transcript-worker", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientRemoteException("Interrupted while calling transcript-worker", e);
        }
    }

    private RemoteResponse executeWithRetry(RemoteCall call) {
        int attempts = Math.max(1, properties.getMaxRetries() + 1);
        RuntimeException last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                RemoteResponse response = call.execute();
                if (response.statusCode() >= 500) {
                    throw new TransientRemoteException(
                            "transcript-worker returned " + response.statusCode(), null);
                }
                return response;
            } catch (TransientRemoteException ex) {
                last = ex;
                if (attempt + 1 >= attempts) {
                    break;
                }
                sleepBackoff(attempt);
            }
        }
        throw last == null
                ? new IllegalStateException("transcript-worker call failed without exception")
                : last;
    }

    private void sleepBackoff(int attempt) {
        long base = Math.max(1L, properties.getRetryBackoff().toMillis());
        long jitter = ThreadLocalRandom.current().nextLong(base + 1);
        long sleep = Math.min(5_000L, (base << Math.min(attempt, 4)) + jitter);
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientRemoteException("Interrupted during retry backoff", e);
        }
    }

    private static byte[] multipartBody(String boundary, String filename, String contentType, byte[] content) {
        String safeName = filename == null || filename.isBlank() ? "upload.vtt" : filename;
        String mime = contentType == null || contentType.isBlank() ? "text/vtt" : contentType;
        String preamble = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n";
        String epilogue = "\r\n--" + boundary + "--\r\n";
        byte[] pre = preamble.getBytes(StandardCharsets.UTF_8);
        byte[] epi = epilogue.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[pre.length + content.length + epi.length];
        System.arraycopy(pre, 0, body, 0, pre.length);
        System.arraycopy(content, 0, body, pre.length, content.length);
        System.arraycopy(epi, 0, body, pre.length + content.length, epi.length);
        return body;
    }

    @FunctionalInterface
    private interface RemoteCall {
        RemoteResponse execute();
    }

    public record RemoteResponse(int statusCode, byte[] body, String contentType) {
    }

    public static final class TransientRemoteException extends RuntimeException {
        public TransientRemoteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
