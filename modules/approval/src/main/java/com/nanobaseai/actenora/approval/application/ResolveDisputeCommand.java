package com.nanobaseai.actenora.approval.application;

import java.util.UUID;

public record ResolveDisputeCommand(
        UUID tenantId,
        UUID disputeId,
        String resolverId,
        boolean accept
) {
}
