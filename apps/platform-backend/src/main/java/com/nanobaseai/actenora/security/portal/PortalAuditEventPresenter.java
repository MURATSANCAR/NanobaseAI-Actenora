package com.nanobaseai.actenora.security.portal;

import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.UserView;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.BusinessContextResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps append-only audit entries to operator-friendly portal views.
 */
final class PortalAuditEventPresenter {

    private final IdentityApi identityApi;
    private final MeetingApi meetingApi;

    PortalAuditEventPresenter(IdentityApi identityApi, MeetingApi meetingApi) {
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
        this.meetingApi = Objects.requireNonNull(meetingApi, "meetingApi");
    }

    PortalApiController.AuditEventView present(AuditApi.AuditTimelineEntry entry, UUID tenantId, Map<UUID, String> actorNames) {
        String actorName = resolveActorName(entry.actorId(), tenantId, actorNames);
        String resourceLabel = resolveResourceLabel(entry, tenantId);
        return new PortalApiController.AuditEventView(
                entry.id(),
                entry.action(),
                actorName,
                resourceLabel,
                entry.resourceType(),
                entry.resourceId().toString(),
                entry.occurredAt().toString()
        );
    }

    private String resolveActorName(String actorId, UUID tenantId, Map<UUID, String> actorNames) {
        if (actorId == null || actorId.isBlank()) {
            return "Sistem";
        }
        try {
            UUID userId = UUID.fromString(actorId.trim());
            return actorNames.computeIfAbsent(userId, id ->
                    identityApi.findById(TenantId.of(tenantId), id)
                            .map(UserView::displayName)
                            .filter(name -> !name.isBlank())
                            .orElse(fallbackActorLabel(actorId)));
        } catch (IllegalArgumentException ex) {
            return fallbackActorLabel(actorId);
        }
    }

    private static String fallbackActorLabel(String actorId) {
        String normalized = actorId.trim().toLowerCase(Locale.ROOT);
        if ("system".equals(normalized) || "00000000-0000-0000-0000-000000000001".equals(normalized)) {
            return "Sistem";
        }
        return actorId;
    }

    private String resolveResourceLabel(AuditApi.AuditTimelineEntry entry, UUID tenantId) {
        String fromMetadata = metadataText(entry.metadata(), "title", "name", "displayName", "subject", "referenceCode");
        if (fromMetadata != null) {
            return fromMetadata;
        }
        return switch (entry.resourceType()) {
            case "MeetingOccurrence", "Meeting" -> lookupMeetingTitle(entry.resourceId());
            case "BusinessContext" -> lookupBusinessContextName(entry.resourceId());
            default -> humanizeResourceType(entry.resourceType());
        };
    }

    private String lookupMeetingTitle(UUID meetingId) {
        try {
            MeetingResponse meeting = meetingApi.getMeeting(meetingId);
            if (meeting.title() != null && !meeting.title().isBlank()) {
                return meeting.title();
            }
        } catch (RuntimeException ignored) {
            /* fall through */
        }
        return "Toplantı";
    }

    private String lookupBusinessContextName(UUID contextId) {
        try {
            for (BusinessContextResponse context : meetingApi.listBusinessContexts()) {
                if (context.id().equals(contextId) && context.name() != null && !context.name().isBlank()) {
                    return context.name();
                }
            }
        } catch (RuntimeException ignored) {
            /* fall through */
        }
        return "İş bağlamı";
    }

    private static String metadataText(Map<String, Object> metadata, String... keys) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static String humanizeResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return "Kayıt";
        }
        return resourceType
                .replace("Occurrence", "")
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();
    }

    static Map<UUID, String> preloadActorNames(UUID tenantId, IdentityApi identityApi) {
        Map<UUID, String> names = new HashMap<>();
        for (UserView user : identityApi.listUsers(TenantId.of(tenantId))) {
            names.put(user.id(), user.displayName());
        }
        return names;
    }
}
