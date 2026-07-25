package com.nanobaseai.actenora.transcript.infrastructure.storage;

import com.nanobaseai.actenora.sharedkernel.port.storage.AuthorizedUrl;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectMetadata;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectPutRequest;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorageException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * MinIO-compatible ObjectStorage via AWS SDK S3 client.
 */
public final class S3CompatibleObjectStorage implements ObjectStorage, AutoCloseable {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3CompatibleObjectStorage(
            URI endpoint,
            String region,
            String accessKey,
            String secretKey,
            String bucket) {
        this.bucket = bucket;
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        this.s3 = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Config)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Config)
                .build();
    }

    S3CompatibleObjectStorage(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public ObjectMetadata put(ObjectPutRequest request) {
        try {
            if (request.immutable() && exists(request.key())) {
                throw ObjectStorageException.alreadyExists(request.key());
            }
            Map<String, String> meta = new HashMap<>(request.userMetadata());
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(request.key())
                    .contentType(request.contentType())
                    .contentLength(request.contentLength())
                    .metadata(meta)
                    .build();
            var response = s3.putObject(put, RequestBody.fromInputStream(request.content(), request.contentLength()));
            Instant retentionUntil = null;
            if (meta.containsKey("retention-until")) {
                retentionUntil = Instant.parse(meta.get("retention-until"));
            }
            return new ObjectMetadata(
                    request.key(),
                    request.contentLength(),
                    request.contentType(),
                    response.eTag(),
                    Instant.now(),
                    meta,
                    retentionUntil);
        } catch (ObjectStorageException e) {
            throw e;
        } catch (SdkClientException e) {
            if (isTimeout(e)) {
                throw ObjectStorageException.timeout(request.key(), e);
            }
            throw new ObjectStorageException("OBJECT_STORAGE_ERROR", "Put failed for key=" + request.key(), e);
        } catch (S3Exception e) {
            throw new ObjectStorageException("OBJECT_STORAGE_ERROR", "Put failed for key=" + request.key(), e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            throw ObjectStorageException.notFound(key);
        } catch (SdkClientException e) {
            if (isTimeout(e)) {
                throw ObjectStorageException.timeout(key, e);
            }
            throw new ObjectStorageException("OBJECT_STORAGE_ERROR", "Get failed for key=" + key, e);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw ObjectStorageException.notFound(key);
            }
            throw new ObjectStorageException("OBJECT_STORAGE_ERROR", "Get failed for key=" + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkClientException e) {
            if (isTimeout(e)) {
                throw ObjectStorageException.timeout(key, e);
            }
            throw new ObjectStorageException("OBJECT_STORAGE_ERROR", "Delete failed for key=" + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new ObjectStorageException("OBJECT_STORAGE_ERROR", "Exists check failed for key=" + key, e);
        } catch (SdkClientException e) {
            if (isTimeout(e)) {
                throw ObjectStorageException.timeout(key, e);
            }
            throw new ObjectStorageException("OBJECT_STORAGE_ERROR", "Exists check failed for key=" + key, e);
        }
    }

    @Override
    public Optional<ObjectMetadata> metadata(String key) {
        try {
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            Map<String, String> meta = head.metadata() == null ? Map.of() : head.metadata();
            Instant retentionUntil = null;
            if (meta.containsKey("retention-until")) {
                retentionUntil = Instant.parse(meta.get("retention-until"));
            }
            return Optional.of(new ObjectMetadata(
                    key,
                    head.contentLength() == null ? 0L : head.contentLength(),
                    head.contentType() == null ? "application/octet-stream" : head.contentType(),
                    head.eTag(),
                    head.lastModified(),
                    meta,
                    retentionUntil));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw new ObjectStorageException("OBJECT_STORAGE_ERROR", "Metadata failed for key=" + key, e);
        } catch (SdkClientException e) {
            if (isTimeout(e)) {
                throw ObjectStorageException.timeout(key, e);
            }
            throw new ObjectStorageException("OBJECT_STORAGE_ERROR", "Metadata failed for key=" + key, e);
        }
    }

    @Override
    public AuthorizedUrl generateAuthorizedUrl(String key, Duration ttl) {
        try {
            if (!exists(key)) {
                throw ObjectStorageException.notFound(key);
            }
            var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build());
            return new AuthorizedUrl(presigned.url().toURI(), Instant.now().plus(ttl));
        } catch (ObjectStorageException e) {
            throw e;
        } catch (Exception e) {
            if (isTimeout(e)) {
                throw ObjectStorageException.timeout(key, e);
            }
            throw new ObjectStorageException("OBJECT_STORAGE_ERROR", "Presign failed for key=" + key, e);
        }
    }

    @Override
    public void close() {
        s3.close();
        presigner.close();
    }

    private static boolean isTimeout(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String name = cur.getClass().getSimpleName().toLowerCase();
            String msg = cur.getMessage() == null ? "" : cur.getMessage().toLowerCase();
            if (name.contains("timeout") || msg.contains("timed out") || msg.contains("timeout")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
