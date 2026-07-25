package com.nanobaseai.actenora.template.infrastructure.storage;

import com.nanobaseai.actenora.sharedkernel.port.storage.AuthorizedUrl;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectMetadata;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectPutRequest;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorageException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
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
 * MinIO-compatible ObjectStorage via AWS SDK S3 client (path-style).
 * Prefer a single shared ObjectStorage bean in platform-backend; this adapter
 * exists so the template/document-renderer worker can run standalone.
 */
public final class MinioObjectStorage implements ObjectStorage, AutoCloseable {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public MinioObjectStorage(URI endpoint, String region, String accessKey, String secretKey, String bucket) {
        this.bucket = bucket;
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
        S3Configuration s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();
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

    @Override
    public ObjectMetadata put(ObjectPutRequest request) {
        try {
            if (request.immutable() && exists(request.key())) {
                throw ObjectStorageException.alreadyExists(request.key());
            }
            Map<String, String> meta = new HashMap<>(request.userMetadata());
            PutObjectRequest.Builder put = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(request.key())
                    .contentType(request.contentType())
                    .contentLength(request.contentLength())
                    .metadata(meta);
            s3.putObject(put.build(), RequestBody.fromInputStream(request.content(), request.contentLength()));
            return metadata(request.key()).orElseThrow(() ->
                    new ObjectStorageException("OBJECT_STORAGE_IO", "Put succeeded but metadata missing"));
        } catch (ObjectStorageException ex) {
            throw ex;
        } catch (SdkClientException | S3Exception ex) {
            throw map(request.key(), ex);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException ex) {
            throw ObjectStorageException.notFound(key);
        } catch (SdkClientException | S3Exception ex) {
            throw map(key, ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkClientException | S3Exception ex) {
            throw map(key, ex);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw map(key, ex);
        } catch (SdkClientException ex) {
            throw map(key, ex);
        }
    }

    @Override
    public Optional<ObjectMetadata> metadata(String key) {
        try {
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            Instant retentionUntil = null;
            if (head.metadata() != null && head.metadata().get("retention-until") != null) {
                retentionUntil = Instant.parse(head.metadata().get("retention-until"));
            }
            return Optional.of(new ObjectMetadata(
                    key,
                    head.contentLength() == null ? 0L : head.contentLength(),
                    head.contentType() == null ? "application/octet-stream" : head.contentType(),
                    head.eTag(),
                    head.lastModified(),
                    head.metadata() == null ? Map.of() : head.metadata(),
                    retentionUntil));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return Optional.empty();
            }
            throw map(key, ex);
        } catch (SdkClientException ex) {
            throw map(key, ex);
        }
    }

    @Override
    public AuthorizedUrl generateAuthorizedUrl(String key, Duration ttl) {
        try {
            var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build());
            return new AuthorizedUrl(presigned.url().toURI(), Instant.now().plus(ttl));
        } catch (Exception ex) {
            throw new ObjectStorageException("OBJECT_STORAGE_SIGN", "Failed to sign URL for key=" + key, ex);
        }
    }

    @Override
    public void close() {
        s3.close();
        presigner.close();
    }

    private static ObjectStorageException map(String key, RuntimeException ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        if (message.toLowerCase().contains("timed out") || message.toLowerCase().contains("timeout")) {
            return ObjectStorageException.timeout(key, ex);
        }
        return new ObjectStorageException("OBJECT_STORAGE_ERROR", "Object storage error for key=" + key + ": " + message, ex);
    }
}
