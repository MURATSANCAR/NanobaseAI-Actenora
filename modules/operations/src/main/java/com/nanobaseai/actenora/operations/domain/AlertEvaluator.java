package com.nanobaseai.actenora.operations.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates alert thresholds against live ops signals (FAZ 25).
 */
public final class AlertEvaluator {

    private final AlertThresholds thresholds;

    public AlertEvaluator(AlertThresholds thresholds) {
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds");
    }

    public AlertThresholds thresholds() {
        return thresholds;
    }

    public List<OpsAlert> evaluate(
            Instant now,
            List<CertificateRecord> certificates,
            List<SlaObservation> slaObservations,
            long dlqDepth,
            long aiQueueDepth,
            long transcriptPendingAgeSeconds,
            List<ModelPoolMember> modelPool
    ) {
        Objects.requireNonNull(now, "now");
        java.util.ArrayList<OpsAlert> alerts = new java.util.ArrayList<>();

        for (CertificateRecord cert : certificates) {
            if (cert.isExpired(now)) {
                alerts.add(alert(
                        AlertType.CERTIFICATE_EXPIRY,
                        AlertSeverity.CRITICAL,
                        "Certificate expired: " + cert.name(),
                        "subject=" + cert.subject() + " expiredAt=" + cert.expiresAt(),
                        now
                ));
            } else if (cert.expiresWithin(now, thresholds.certificateExpiryWarning())) {
                alerts.add(alert(
                        AlertType.CERTIFICATE_EXPIRY,
                        AlertSeverity.WARNING,
                        "Certificate expiring: " + cert.name(),
                        "subject=" + cert.subject() + " expiresAt=" + cert.expiresAt(),
                        now
                ));
            }
        }

        for (SlaObservation observation : slaObservations) {
            if (observation.isBreached()) {
                alerts.add(alert(
                        AlertType.SLA_BREACH,
                        AlertSeverity.CRITICAL,
                        "SLA breach for meeting " + observation.meetingId(),
                        "actual=" + observation.actual() + " target=" + observation.target(),
                        now
                ));
            }
        }

        if (dlqDepth >= thresholds.dlqDepthCritical()) {
            alerts.add(alert(
                    AlertType.DLQ_DEPTH,
                    AlertSeverity.CRITICAL,
                    "DLQ depth critical",
                    "depth=" + dlqDepth + " threshold=" + thresholds.dlqDepthCritical(),
                    now
            ));
        } else if (dlqDepth >= thresholds.dlqDepthWarning()) {
            alerts.add(alert(
                    AlertType.DLQ_DEPTH,
                    AlertSeverity.WARNING,
                    "DLQ depth warning",
                    "depth=" + dlqDepth + " threshold=" + thresholds.dlqDepthWarning(),
                    now
            ));
        }

        if (aiQueueDepth >= thresholds.aiQueueDepthWarning()) {
            alerts.add(alert(
                    AlertType.QUEUE_DEPTH,
                    AlertSeverity.WARNING,
                    "AI queue depth high",
                    "depth=" + aiQueueDepth + " threshold=" + thresholds.aiQueueDepthWarning(),
                    now
            ));
        }

        if (transcriptPendingAgeSeconds >= thresholds.transcriptPendingAgeWarningSeconds()) {
            alerts.add(alert(
                    AlertType.QUEUE_DEPTH,
                    AlertSeverity.WARNING,
                    "Transcript pending age high",
                    "ageSeconds=" + transcriptPendingAgeSeconds
                            + " threshold=" + thresholds.transcriptPendingAgeWarningSeconds(),
                    now
            ));
        }

        long unhealthy = modelPool.stream().filter(m -> !m.healthy()).count();
        if (unhealthy > 0) {
            alerts.add(alert(
                    AlertType.DEPLOYMENT_UNHEALTHY,
                    AlertSeverity.WARNING,
                    "Unhealthy model deployments",
                    "unhealthy=" + unhealthy + " total=" + modelPool.size(),
                    now
            ));
        }

        return List.copyOf(alerts);
    }

    private static OpsAlert alert(
            AlertType type,
            AlertSeverity severity,
            String title,
            String detail,
            Instant now
    ) {
        return new OpsAlert(java.util.UUID.randomUUID(), type, severity, title, detail, now, false);
    }
}
