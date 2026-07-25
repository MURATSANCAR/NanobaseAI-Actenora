package com.nanobaseai.actenora.operations.domain.retention;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Decides whether a retention candidate may be deleted given active legal holds.
 */
public final class RetentionGuard {

    private RetentionGuard() {
    }

    public static void assertDeletable(RetentionCandidate candidate, List<LegalHold> activeHolds) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(activeHolds, "activeHolds");
        for (LegalHold hold : activeHolds) {
            if (hold.covers(candidate)) {
                throw new LegalHoldBlockedException(
                        candidate.resourceType().name(),
                        candidate.resourceId());
            }
        }
    }

    public static boolean isExpiredAndNotHeld(
            RetentionCandidate candidate,
            Instant now,
            List<LegalHold> activeHolds
    ) {
        if (!candidate.isExpired(now)) {
            return false;
        }
        try {
            assertDeletable(candidate, activeHolds);
            return true;
        } catch (LegalHoldBlockedException ex) {
            return false;
        }
    }
}
