package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphProperties;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Durable settings store ({@code actenora.persistence.mode=jdbc}). The client secret is encrypted
 * via {@link SecretCipher}; a save is rejected when a secret is present but no encryption key is
 * configured, so we never persist a plaintext credential to the database.
 */
public final class JdbcGraphConnectionSettingsStore implements GraphConnectionSettingsStore {

    private static final String SCOPE_KEY = "global";

    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;

    public JdbcGraphConnectionSettingsStore(JdbcTemplate jdbc, SecretCipher cipher) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
    }

    @Override
    public Optional<GraphConnectionSettings> load() {
        List<GraphConnectionSettings> rows = jdbc.query(
                """
                SELECT enabled, graph_base_url, authority_host, azure_tenant_id, client_id,
                       graph_scope, auth_mode, client_secret_cipher, certificate_pem_path,
                       private_key_pem_path, default_mailbox_user_id
                FROM microsoftconnection.graph_connection_settings
                WHERE scope_key = ?
                """,
                (rs, rowNum) -> new GraphConnectionSettings(
                        rs.getBoolean("enabled"),
                        rs.getString("graph_base_url"),
                        rs.getString("authority_host"),
                        rs.getString("azure_tenant_id"),
                        rs.getString("client_id"),
                        rs.getString("graph_scope"),
                        MicrosoftGraphProperties.AuthMode.valueOf(rs.getString("auth_mode")),
                        Optional.ofNullable(cipher.decrypt(rs.getString("client_secret_cipher"))),
                        Optional.ofNullable(rs.getString("certificate_pem_path")),
                        Optional.ofNullable(rs.getString("private_key_pem_path")),
                        Optional.ofNullable(rs.getString("default_mailbox_user_id"))
                ),
                SCOPE_KEY);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public void save(GraphConnectionSettings settings) {
        Objects.requireNonNull(settings, "settings");
        String secretCipher = null;
        if (settings.clientSecret().isPresent()) {
            if (!cipher.available()) {
                throw new IllegalStateException(
                        "Cannot persist client secret: set actenora.secrets.encryption-key "
                                + "(base64 AES key) or use certificate auth.");
            }
            secretCipher = cipher.encrypt(settings.clientSecret().get());
        }
        jdbc.update(
                """
                INSERT INTO microsoftconnection.graph_connection_settings (
                    scope_key, enabled, graph_base_url, authority_host, azure_tenant_id, client_id,
                    graph_scope, auth_mode, client_secret_cipher, certificate_pem_path,
                    private_key_pem_path, default_mailbox_user_id, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (scope_key) DO UPDATE SET
                    enabled = EXCLUDED.enabled,
                    graph_base_url = EXCLUDED.graph_base_url,
                    authority_host = EXCLUDED.authority_host,
                    azure_tenant_id = EXCLUDED.azure_tenant_id,
                    client_id = EXCLUDED.client_id,
                    graph_scope = EXCLUDED.graph_scope,
                    auth_mode = EXCLUDED.auth_mode,
                    client_secret_cipher = EXCLUDED.client_secret_cipher,
                    certificate_pem_path = EXCLUDED.certificate_pem_path,
                    private_key_pem_path = EXCLUDED.private_key_pem_path,
                    default_mailbox_user_id = EXCLUDED.default_mailbox_user_id,
                    updated_at = EXCLUDED.updated_at
                """,
                SCOPE_KEY,
                settings.enabled(),
                settings.graphBaseUrl(),
                settings.authorityHost(),
                settings.tenantId(),
                settings.clientId(),
                settings.scope(),
                settings.authMode().name(),
                secretCipher,
                settings.certificatePemPath().orElse(null),
                settings.privateKeyPemPath().orElse(null),
                settings.defaultMailboxUserId().orElse(null),
                Timestamp.from(Instant.now()));
    }
}
