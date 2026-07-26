package com.nanobaseai.actenora.policy.infrastructure.persistence;

import com.nanobaseai.actenora.policy.application.QuotaUsagePort;
import com.nanobaseai.actenora.policy.domain.QuotaDimension;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Objects;

/** JDBC quota usage store ({@code policy.quota_usage_daily}, {@code policy.concurrency_usage}). */
public final class JdbcQuotaUsageStore implements QuotaUsagePort {

    private final JdbcTemplate jdbc;

    public JdbcQuotaUsageStore(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public long getUsage(TenantId tenantId, QuotaDimension dimension, LocalDate day) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(day, "day");
        Long value = jdbc.queryForObject(
                """
                        SELECT used_amount FROM policy.quota_usage_daily
                        WHERE tenant_id = ? AND usage_day = ? AND dimension = ?
                        """,
                Long.class,
                tenantId.value(),
                day,
                dimension.name()
        );
        return value == null ? 0L : value;
    }

    @Override
    public void addUsage(TenantId tenantId, QuotaDimension dimension, LocalDate day, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        String sql = """
                INSERT INTO policy.quota_usage_daily (tenant_id, usage_day, dimension, used_amount)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, usage_day, dimension) DO UPDATE SET
                    used_amount = policy.quota_usage_daily.used_amount + EXCLUDED.used_amount
                """;
        jdbc.update(sql, tenantId.value(), day, dimension.name(), amount);
    }

    @Override
    public int getConcurrentAiJobs(TenantId tenantId) {
        Integer value = jdbc.queryForObject(
                "SELECT concurrent_ai_jobs FROM policy.concurrency_usage WHERE tenant_id = ?",
                Integer.class,
                tenantId.value()
        );
        return value == null ? 0 : value;
    }

    @Override
    public void setConcurrentAiJobs(TenantId tenantId, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        String sql = """
                INSERT INTO policy.concurrency_usage (tenant_id, concurrent_ai_jobs, updated_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (tenant_id) DO UPDATE SET
                    concurrent_ai_jobs = EXCLUDED.concurrent_ai_jobs,
                    updated_at = NOW()
                """;
        jdbc.update(sql, tenantId.value(), count);
    }
}
