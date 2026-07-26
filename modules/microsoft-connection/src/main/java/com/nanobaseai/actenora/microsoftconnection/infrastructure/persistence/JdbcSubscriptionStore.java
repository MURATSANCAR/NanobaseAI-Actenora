package com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence;

import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcSubscriptionStore implements SubscriptionStore {

    private static final String COLUMNS = """
            tenant_id, subscription_id, resource, change_type, notification_url,
            client_state, expiration_date_time, application_id
            """;

    private static final RowMapper<GraphSubscription> ROW_MAPPER = (rs, rowNum) -> new GraphSubscription(
            rs.getObject("tenant_id", UUID.class),
            rs.getString("subscription_id"),
            rs.getString("resource"),
            rs.getString("change_type"),
            rs.getString("notification_url"),
            rs.getString("client_state"),
            rs.getTimestamp("expiration_date_time").toInstant(),
            rs.getString("application_id")
    );

    private final JdbcTemplate jdbc;

    public JdbcSubscriptionStore(JdbcTemplate jdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public void save(GraphSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription");
        jdbc.update(
                """
                        INSERT INTO microsoftconnection.graph_subscription (
                            tenant_id, subscription_id, resource, change_type, notification_url,
                            client_state, expiration_date_time, application_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (tenant_id, subscription_id) DO UPDATE SET
                            resource = EXCLUDED.resource,
                            change_type = EXCLUDED.change_type,
                            notification_url = EXCLUDED.notification_url,
                            client_state = EXCLUDED.client_state,
                            expiration_date_time = EXCLUDED.expiration_date_time,
                            application_id = EXCLUDED.application_id
                        """,
                subscription.tenantId(),
                subscription.subscriptionId(),
                subscription.resource(),
                subscription.changeType(),
                subscription.notificationUrl(),
                subscription.clientState(),
                Timestamp.from(subscription.expirationDateTime()),
                subscription.applicationId()
        );
    }

    @Override
    public Optional<GraphSubscription> findById(UUID tenantId, String subscriptionId) {
        List<GraphSubscription> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM microsoftconnection.graph_subscription WHERE tenant_id = ? AND subscription_id = ?",
                ROW_MAPPER,
                tenantId,
                subscriptionId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<GraphSubscription> findBySubscriptionId(String subscriptionId) {
        List<GraphSubscription> rows = jdbc.query(
                "SELECT " + COLUMNS
                        + " FROM microsoftconnection.graph_subscription WHERE subscription_id = ? LIMIT 2",
                ROW_MAPPER,
                subscriptionId
        );
        if (rows.size() > 1) {
            throw new IllegalStateException("Graph subscription id is not unique: " + subscriptionId);
        }
        return rows.stream().findFirst();
    }

    @Override
    public List<GraphSubscription> findExpiringBefore(Instant threshold) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM microsoftconnection.graph_subscription WHERE expiration_date_time < ?",
                ROW_MAPPER,
                Timestamp.from(threshold)
        );
    }

    @Override
    public List<GraphSubscription> findAllForTenant(UUID tenantId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM microsoftconnection.graph_subscription WHERE tenant_id = ?",
                ROW_MAPPER,
                tenantId
        );
    }

    @Override
    public List<UUID> distinctTenantIds() {
        return jdbc.queryForList(
                "SELECT DISTINCT tenant_id FROM microsoftconnection.graph_subscription",
                UUID.class
        );
    }
}
