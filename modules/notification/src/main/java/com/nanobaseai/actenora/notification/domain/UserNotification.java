package com.nanobaseai.actenora.notification.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class UserNotification {

    private final UUID id;
    private final TenantId tenantId;
    private final String recipientOid;
    private final UserNotificationType type;
    private final String title;
    private final String body;
    private final String href;
    private final String dedupeKey;
    private final Instant createdAt;
    private Instant readAt;

    private UserNotification(
            UUID id,
            TenantId tenantId,
            String recipientOid,
            UserNotificationType type,
            String title,
            String body,
            String href,
            String dedupeKey,
            Instant createdAt,
            Instant readAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.recipientOid = requireText(recipientOid, "recipientOid");
        this.type = Objects.requireNonNull(type, "type");
        this.title = requireText(title, "title");
        this.body = body == null ? "" : body;
        this.href = href == null || href.isBlank() ? "/" : href.trim();
        this.dedupeKey = requireText(dedupeKey, "dedupeKey");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.readAt = readAt;
    }

    public static UserNotification create(
            TenantId tenantId,
            String recipientOid,
            UserNotificationType type,
            String title,
            String body,
            String href,
            String dedupeKey,
            Instant now
    ) {
        return new UserNotification(
                UUID.randomUUID(),
                tenantId,
                recipientOid,
                type,
                title,
                body,
                href,
                dedupeKey,
                now,
                null
        );
    }

    public static UserNotification rehydrate(
            UUID id,
            TenantId tenantId,
            String recipientOid,
            UserNotificationType type,
            String title,
            String body,
            String href,
            String dedupeKey,
            Instant createdAt,
            Instant readAt
    ) {
        return new UserNotification(
                id, tenantId, recipientOid, type, title, body, href, dedupeKey, createdAt, readAt
        );
    }

    public void markRead(Instant now) {
        Objects.requireNonNull(now, "now");
        if (readAt == null) {
            readAt = now;
        }
    }

    public boolean isUnread() {
        return readAt == null;
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String recipientOid() {
        return recipientOid;
    }

    public UserNotificationType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public String href() {
        return href;
    }

    public String dedupeKey() {
        return dedupeKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> readAt() {
        return Optional.ofNullable(readAt);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
