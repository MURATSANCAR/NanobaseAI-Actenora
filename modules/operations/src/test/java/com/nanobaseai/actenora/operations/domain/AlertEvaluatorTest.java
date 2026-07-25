package com.nanobaseai.actenora.operations.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertEvaluatorTest {

    @Test
    void certificateExpiryWarningAndExpiredCritical() {
        Instant now = Instant.parse("2026-07-25T12:00:00Z");
        AlertEvaluator evaluator = new AlertEvaluator(AlertThresholds.defaults());
        List<OpsAlert> alerts = evaluator.evaluate(
                now,
                List.of(
                        new CertificateRecord("soon", now.plus(Duration.ofDays(10)), "CN=soon"),
                        new CertificateRecord("expired", now.minus(Duration.ofDays(1)), "CN=expired")
                ),
                List.of(),
                0,
                0,
                0,
                List.of()
        );
        assertEquals(2, alerts.size());
        assertTrue(alerts.stream().anyMatch(a ->
                a.type() == AlertType.CERTIFICATE_EXPIRY && a.severity() == AlertSeverity.WARNING));
        assertTrue(alerts.stream().anyMatch(a ->
                a.type() == AlertType.CERTIFICATE_EXPIRY && a.severity() == AlertSeverity.CRITICAL));
    }

    @Test
    void slaBreachOnlyWhenOverTarget() {
        Instant now = Instant.parse("2026-07-25T12:00:00Z");
        AlertEvaluator evaluator = new AlertEvaluator(AlertThresholds.defaults());
        SlaObservation ok = new SlaObservation(
                UUID.randomUUID(),
                TenantId.random(),
                now.minus(Duration.ofMinutes(30)),
                now,
                Duration.ofMinutes(60)
        );
        SlaObservation breach = new SlaObservation(
                UUID.randomUUID(),
                TenantId.random(),
                now.minus(Duration.ofMinutes(90)),
                now,
                Duration.ofMinutes(60)
        );
        List<OpsAlert> alerts = evaluator.evaluate(
                now,
                List.of(),
                List.of(ok, breach),
                0,
                0,
                0,
                List.of()
        );
        assertEquals(1, alerts.size());
        assertEquals(AlertType.SLA_BREACH, alerts.getFirst().type());
        assertFalse(ok.isBreached());
        assertTrue(breach.isBreached());
    }
}
