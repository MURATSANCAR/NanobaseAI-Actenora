package com.nanobaseai.actenora.modelmanagement.infrastructure.persistence;

import com.nanobaseai.actenora.modelmanagement.application.ModelDefinitionRepository;
import com.nanobaseai.actenora.modelmanagement.domain.ModelCapability;
import com.nanobaseai.actenora.modelmanagement.domain.ModelCapabilityType;
import com.nanobaseai.actenora.modelmanagement.domain.ModelDefinition;
import com.nanobaseai.actenora.modelmanagement.domain.ModelRegistryException;
import com.nanobaseai.actenora.modelmanagement.domain.ModelStatus;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** JDBC model definition store ({@code modelmanagement.model_definition}). */
public final class JdbcModelDefinitionRepository implements ModelDefinitionRepository {

    private static final String DEF_COLUMNS = """
            id, model_key, display_name, provider_type, served_model_id, model_family,
            parameter_size, quantization, context_window, max_output_tokens, supported_languages,
            status, priority, quality_score, speed_score, created_at, updated_at, version
            """;

    private static final RowMapper<DefinitionRow> DEF_ROW_MAPPER = (rs, rowNum) -> new DefinitionRow(
            rs.getObject("id", UUID.class),
            rs.getString("model_key"),
            rs.getString("display_name"),
            rs.getString("provider_type"),
            rs.getString("served_model_id"),
            rs.getString("model_family"),
            rs.getString("parameter_size"),
            rs.getString("quantization"),
            rs.getInt("context_window"),
            rs.getInt("max_output_tokens"),
            rs.getString("supported_languages"),
            ModelStatus.valueOf(rs.getString("status")),
            rs.getInt("priority"),
            rs.getDouble("quality_score"),
            rs.getDouble("speed_score"),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "updated_at"),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcModelDefinitionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public Optional<ModelDefinition> findByKey(String modelKey) {
        String sql = "SELECT " + DEF_COLUMNS + " FROM modelmanagement.model_definition WHERE model_key = ?";
        return jdbc.query(sql, DEF_ROW_MAPPER, modelKey).stream()
                .findFirst()
                .map(row -> toDefinition(row, loadCapabilities(row.id())));
    }

    @Override
    public Optional<ModelDefinition> findById(UUID id) {
        String sql = "SELECT " + DEF_COLUMNS + " FROM modelmanagement.model_definition WHERE id = ?";
        return jdbc.query(sql, DEF_ROW_MAPPER, id).stream()
                .findFirst()
                .map(row -> toDefinition(row, loadCapabilities(row.id())));
    }

    @Override
    public boolean existsByKey(String modelKey) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM modelmanagement.model_definition WHERE model_key = ?",
                Integer.class,
                modelKey
        );
        return count != null && count > 0;
    }

    @Override
    public void save(ModelDefinition definition) {
        jdbc.execute((Connection conn) -> {
            conn.setAutoCommit(false);
            try {
                persistDefinition(conn, definition);
                replaceCapabilities(conn, definition);
                conn.commit();
                return null;
            } catch (RuntimeException ex) {
                conn.rollback();
                throw ex;
            } catch (SQLException ex) {
                conn.rollback();
                throw new IllegalStateException("Failed to save model definition " + definition.id(), ex);
            }
        });
    }

    @Override
    public List<ModelDefinition> findAll() {
        List<DefinitionRow> rows = jdbc.query(
                "SELECT " + DEF_COLUMNS + " FROM modelmanagement.model_definition ORDER BY model_key",
                DEF_ROW_MAPPER
        );
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ModelCapability>> caps = loadCapabilities(rows.stream().map(DefinitionRow::id).toList());
        List<ModelDefinition> definitions = new ArrayList<>(rows.size());
        for (DefinitionRow row : rows) {
            definitions.add(toDefinition(row, caps.getOrDefault(row.id(), List.of())));
        }
        return List.copyOf(definitions);
    }

    private void persistDefinition(Connection conn, ModelDefinition definition) throws SQLException {
        if (definition.version() == 0L) {
            insertDefinition(conn, definition);
            return;
        }
        long previousVersion = definition.version() - 1L;
        String updateSql = """
                UPDATE modelmanagement.model_definition SET
                    display_name = ?, provider_type = ?, served_model_id = ?, model_family = ?,
                    parameter_size = ?, quantization = ?, context_window = ?, max_output_tokens = ?,
                    supported_languages = ?, status = ?, priority = ?, quality_score = ?,
                    speed_score = ?, updated_at = ?, version = ?
                WHERE id = ? AND version = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            int i = 1;
            ps.setString(i++, definition.displayName());
            ps.setString(i++, definition.providerType());
            ps.setString(i++, definition.servedModelId());
            ps.setString(i++, definition.modelFamily());
            ps.setString(i++, definition.parameterSize());
            ps.setString(i++, definition.quantization());
            ps.setInt(i++, definition.contextWindow());
            ps.setInt(i++, definition.maxOutputTokens());
            ps.setString(i++, joinLanguages(definition.supportedLanguages()));
            ps.setString(i++, definition.status().name());
            ps.setInt(i++, definition.priority());
            ps.setDouble(i++, definition.qualityScore());
            ps.setDouble(i++, definition.speedScore());
            ps.setTimestamp(i++, JdbcInstant.toTimestamp(definition.updatedAt()));
            ps.setLong(i++, definition.version());
            ps.setObject(i++, definition.id());
            ps.setLong(i, previousVersion);
            if (ps.executeUpdate() == 1) {
                return;
            }
        }
        throw new ModelRegistryException(
                "OPTIMISTIC_LOCK_CONFLICT",
                "Model definition " + definition.id() + " version conflict (expected " + previousVersion + ")"
        );
    }

    private void insertDefinition(Connection conn, ModelDefinition definition) throws SQLException {
        String insertSql = """
                INSERT INTO modelmanagement.model_definition (
                    id, model_key, display_name, provider_type, served_model_id, model_family,
                    parameter_size, quantization, context_window, max_output_tokens, supported_languages,
                    status, priority, quality_score, speed_score, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            int i = 1;
            ps.setObject(i++, definition.id());
            ps.setString(i++, definition.modelKey());
            ps.setString(i++, definition.displayName());
            ps.setString(i++, definition.providerType());
            ps.setString(i++, definition.servedModelId());
            ps.setString(i++, definition.modelFamily());
            ps.setString(i++, definition.parameterSize());
            ps.setString(i++, definition.quantization());
            ps.setInt(i++, definition.contextWindow());
            ps.setInt(i++, definition.maxOutputTokens());
            ps.setString(i++, joinLanguages(definition.supportedLanguages()));
            ps.setString(i++, definition.status().name());
            ps.setInt(i++, definition.priority());
            ps.setDouble(i++, definition.qualityScore());
            ps.setDouble(i++, definition.speedScore());
            ps.setTimestamp(i++, JdbcInstant.toTimestamp(definition.createdAt()));
            ps.setTimestamp(i++, JdbcInstant.toTimestamp(definition.updatedAt()));
            ps.setLong(i, definition.version());
            ps.executeUpdate();
        }
    }

    private void replaceCapabilities(Connection conn, ModelDefinition definition) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM modelmanagement.model_capability WHERE model_definition_id = ?"
        )) {
            delete.setObject(1, definition.id());
            delete.executeUpdate();
        }
        if (definition.capabilities().isEmpty()) {
            return;
        }
        String insertSql = """
                INSERT INTO modelmanagement.model_capability (
                    model_definition_id, capability, quality_score, speed_score,
                    min_context_required, enabled
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
            for (ModelCapability capability : definition.capabilities().values()) {
                insert.setObject(1, definition.id());
                insert.setString(2, capability.capability().name());
                insert.setDouble(3, capability.qualityScore());
                insert.setDouble(4, capability.speedScore());
                insert.setInt(5, capability.minContextRequired());
                insert.setBoolean(6, capability.enabled());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private List<ModelCapability> loadCapabilities(UUID definitionId) {
        return loadCapabilities(List.of(definitionId)).getOrDefault(definitionId, List.of());
    }

    private Map<UUID, List<ModelCapability>> loadCapabilities(List<UUID> definitionIds) {
        if (definitionIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = definitionIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT model_definition_id, capability, quality_score, speed_score,
                       min_context_required, enabled
                FROM modelmanagement.model_capability
                WHERE model_definition_id IN (%s)
                """.formatted(placeholders);
        Map<UUID, List<ModelCapability>> byDefinition = new HashMap<>();
        jdbc.query(sql, rs -> {
            UUID definitionId = rs.getObject("model_definition_id", UUID.class);
            byDefinition.computeIfAbsent(definitionId, ignored -> new ArrayList<>()).add(new ModelCapability(
                    ModelCapabilityType.valueOf(rs.getString("capability")),
                    rs.getDouble("quality_score"),
                    rs.getDouble("speed_score"),
                    rs.getInt("min_context_required"),
                    rs.getBoolean("enabled")
            ));
        }, definitionIds.toArray());
        return byDefinition;
    }

    private static ModelDefinition toDefinition(DefinitionRow row, List<ModelCapability> capabilities) {
        return ModelDefinition.rehydrate(
                row.id(), row.modelKey(), row.displayName(), row.providerType(), row.servedModelId(),
                row.modelFamily(), row.parameterSize(), row.quantization(), row.contextWindow(),
                row.maxOutputTokens(), parseLanguages(row.supportedLanguages()), row.status(),
                row.priority(), row.qualityScore(), row.speedScore(), row.createdAt(), row.updatedAt(),
                row.version(), capabilities
        );
    }

    private static Set<String> parseLanguages(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String joinLanguages(Set<String> languages) {
        return String.join(",", languages);
    }

    private record DefinitionRow(
            UUID id,
            String modelKey,
            String displayName,
            String providerType,
            String servedModelId,
            String modelFamily,
            String parameterSize,
            String quantization,
            int contextWindow,
            int maxOutputTokens,
            String supportedLanguages,
            ModelStatus status,
            int priority,
            double qualityScore,
            double speedScore,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
    }
}
