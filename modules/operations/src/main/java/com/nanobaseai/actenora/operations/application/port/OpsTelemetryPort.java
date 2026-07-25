package com.nanobaseai.actenora.operations.application.port;

import com.nanobaseai.actenora.operations.domain.CertificateRecord;
import com.nanobaseai.actenora.operations.domain.ModelPoolMember;
import com.nanobaseai.actenora.operations.domain.QueueDepth;
import com.nanobaseai.actenora.operations.domain.RetryEntry;
import com.nanobaseai.actenora.operations.domain.SlaObservation;
import com.nanobaseai.actenora.operations.domain.TenantThroughput;
import com.nanobaseai.actenora.operations.domain.WorkerHealth;

import java.util.List;

/**
 * Read ports feeding the Operations Center dashboards (FAZ 25).
 */
public interface OpsTelemetryPort {

    long meetingCount();

    long transcriptPendingAgeSeconds();

    long aiQueueDepth();

    long mailFailures();

    List<QueueDepth> queueDepths();

    List<WorkerHealth> workers();

    List<RetryEntry> retries(int limit);

    List<ModelPoolMember> modelPool();

    List<CertificateRecord> certificates();

    List<SlaObservation> recentSlaObservations(int limit);

    List<TenantThroughput> tenantThroughput();
}
