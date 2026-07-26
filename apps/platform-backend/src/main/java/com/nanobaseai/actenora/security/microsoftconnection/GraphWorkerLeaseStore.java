package com.nanobaseai.actenora.security.microsoftconnection;

import java.time.Duration;
import java.time.Instant;

public interface GraphWorkerLeaseStore {

    boolean tryAcquire(String leaseName, String ownerId, Instant now, Duration duration);

    void release(String leaseName, String ownerId, Instant now);
}
