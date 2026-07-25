package com.nanobaseai.actenora.approval.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.UUID;

public final class OptimisticLockConflictException extends ActenoraException {

    public OptimisticLockConflictException(UUID resourceId, long expectedVersion) {
        super(
                "OPTIMISTIC_LOCK_CONFLICT",
                "Version conflict for resource " + resourceId + " expectedVersion=" + expectedVersion
        );
    }
}
