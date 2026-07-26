package com.nanobaseai.actenora.identity.infrastructure.persistence;

import com.nanobaseai.actenora.identity.application.port.UserRepositoryPort;
import com.nanobaseai.actenora.identity.domain.OptimisticLockException;
import com.nanobaseai.actenora.identity.domain.SystemRole;
import com.nanobaseai.actenora.identity.domain.User;
import com.nanobaseai.actenora.identity.domain.UserStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class JdbcUserRepository implements UserRepositoryPort {

    private static final String USER_COLUMNS = """
            id, tenant_id, entra_object_id, email, display_name, status,
            created_at, updated_at, version
            """;

    private static final RowMapper<UserRow> USER_ROW_MAPPER = (rs, rowNum) -> new UserRow(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("entra_object_id"),
            rs.getString("email"),
            rs.getString("display_name"),
            UserStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public Optional<User> findById(UUID userId) {
        String sql = "SELECT " + USER_COLUMNS + " FROM identity.users WHERE id = ?";
        List<UserRow> rows = jdbc.query(sql, USER_ROW_MAPPER, userId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toUser(rows.getFirst(), loadRoles(userId)));
    }

    @Override
    public Optional<User> findByEntraObjectId(String entraObjectId) {
        String sql = "SELECT " + USER_COLUMNS + " FROM identity.users WHERE entra_object_id = ?";
        List<UserRow> rows = jdbc.query(sql, USER_ROW_MAPPER, entraObjectId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        UserRow row = rows.getFirst();
        return Optional.of(toUser(row, loadRoles(row.id())));
    }

    @Override
    public List<User> listByTenant(TenantId tenantId) {
        String sql = """
                SELECT %s FROM identity.users
                WHERE tenant_id = ?
                ORDER BY lower(email)
                """.formatted(USER_COLUMNS);
        List<UserRow> rows = jdbc.query(sql, USER_ROW_MAPPER, tenantId.value());
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Set<SystemRole>> rolesByUser = loadRoles(rows.stream().map(UserRow::id).toList());
        List<User> users = new ArrayList<>(rows.size());
        for (UserRow row : rows) {
            users.add(toUser(row, rolesByUser.getOrDefault(row.id(), EnumSet.noneOf(SystemRole.class))));
        }
        return List.copyOf(users);
    }

    @Override
    public void save(User user) {
        jdbc.execute((Connection conn) -> {
            conn.setAutoCommit(false);
            try {
                persistUser(conn, user);
                replaceRoles(conn, user);
                conn.commit();
                return null;
            } catch (RuntimeException ex) {
                conn.rollback();
                throw ex;
            } catch (SQLException ex) {
                conn.rollback();
                throw new IllegalStateException("Failed to save user " + user.id(), ex);
            }
        });
    }

    private void persistUser(Connection conn, User user) throws SQLException {
        if (user.version() == 0L) {
            insertUser(conn, user);
            return;
        }

        long previousVersion = user.version() - 1L;
        String updateSql = """
                UPDATE identity.users SET
                    tenant_id = ?,
                    entra_object_id = ?,
                    email = ?,
                    display_name = ?,
                    status = ?,
                    updated_at = ?,
                    version = ?
                WHERE id = ? AND version = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setObject(1, user.tenantId().value());
            ps.setString(2, user.entraObjectId());
            ps.setString(3, user.email());
            ps.setString(4, user.displayName());
            ps.setString(5, user.status().name());
            ps.setTimestamp(6, Timestamp.from(user.updatedAt()));
            ps.setLong(7, user.version());
            ps.setObject(8, user.id());
            ps.setLong(9, previousVersion);
            if (ps.executeUpdate() == 1) {
                return;
            }
        }

        long actualVersion = readVersion(conn, user.id()).orElse(-1L);
        throw new OptimisticLockException(user.id(), previousVersion, actualVersion);
    }

    private void insertUser(Connection conn, User user) throws SQLException {
        String insertSql = """
                INSERT INTO identity.users (
                    id, tenant_id, entra_object_id, email, display_name, status,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setObject(1, user.id());
            ps.setObject(2, user.tenantId().value());
            ps.setString(3, user.entraObjectId());
            ps.setString(4, user.email());
            ps.setString(5, user.displayName());
            ps.setString(6, user.status().name());
            ps.setTimestamp(7, Timestamp.from(user.createdAt()));
            ps.setTimestamp(8, Timestamp.from(user.updatedAt()));
            ps.setLong(9, user.version());
            ps.executeUpdate();
        }
    }

    private Optional<Long> readVersion(Connection conn, UUID userId) throws SQLException {
        String sql = "SELECT version FROM identity.users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong("version")) : Optional.empty();
            }
        }
    }

    private void replaceRoles(Connection conn, User user) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM identity.user_roles WHERE user_id = ?"
        )) {
            delete.setObject(1, user.id());
            delete.executeUpdate();
        }

        if (user.roles().isEmpty()) {
            return;
        }

        String insertSql = """
                INSERT INTO identity.user_roles (user_id, tenant_id, role_code, granted_at, granted_by)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
            Instant grantedAt = user.updatedAt();
            for (SystemRole role : user.roles()) {
                insert.setObject(1, user.id());
                insert.setObject(2, user.tenantId().value());
                insert.setString(3, role.code());
                insert.setTimestamp(4, Timestamp.from(grantedAt));
                insert.setObject(5, null);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private Set<SystemRole> loadRoles(UUID userId) {
        return loadRoles(List.of(userId)).getOrDefault(userId, EnumSet.noneOf(SystemRole.class));
    }

    private Map<UUID, Set<SystemRole>> loadRoles(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", userIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT user_id, role_code FROM identity.user_roles
                WHERE user_id IN (%s)
                """.formatted(placeholders);
        Object[] args = userIds.toArray();
        Map<UUID, Set<SystemRole>> rolesByUser = new HashMap<>();
        jdbc.query(sql, rs -> {
            UUID userId = rs.getObject("user_id", UUID.class);
            rolesByUser.computeIfAbsent(userId, ignored -> EnumSet.noneOf(SystemRole.class))
                    .add(SystemRole.fromCode(rs.getString("role_code")));
        }, args);
        return rolesByUser;
    }

    private static User toUser(UserRow row, Set<SystemRole> roles) {
        return new User(
                row.id(),
                TenantId.of(row.tenantId()),
                row.entraObjectId(),
                row.email(),
                row.displayName(),
                row.status(),
                row.createdAt(),
                row.updatedAt(),
                row.version(),
                roles
        );
    }

    private record UserRow(
            UUID id,
            UUID tenantId,
            String entraObjectId,
            String email,
            String displayName,
            UserStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
    }
}
