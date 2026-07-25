package com.nanobaseai.actenora.sharedkernel.port.storage;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

/**
 * Storage adapter failures (timeouts, connectivity, vendor errors).
 */
public class ObjectStorageException extends ActenoraException {

    public ObjectStorageException(String code, String message) {
        super(code, message);
    }

    public ObjectStorageException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public static ObjectStorageException timeout(String key, Throwable cause) {
        return new ObjectStorageException(
                "OBJECT_STORAGE_TIMEOUT",
                "Object storage timed out for key=" + key,
                cause);
    }

    public static ObjectStorageException notFound(String key) {
        return new ObjectStorageException(
                "OBJECT_STORAGE_NOT_FOUND",
                "Object not found for key=" + key);
    }

    public static ObjectStorageException alreadyExists(String key) {
        return new ObjectStorageException(
                "OBJECT_STORAGE_ALREADY_EXISTS",
                "Immutable object already exists for key=" + key);
    }
}
