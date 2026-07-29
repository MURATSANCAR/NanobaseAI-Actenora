package com.nanobaseai.actenora.policy.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Maps SLA levels to target processing latency (minutes). */
public record ProcessingSlaPolicy(
        SlaLevel defaultLevel,
        Map<SlaLevel, Integer> targetLatencyMinutes
) {
    public ProcessingSlaPolicy {
        Objects.requireNonNull(defaultLevel, "defaultLevel");
        Objects.requireNonNull(targetLatencyMinutes, "targetLatencyMinutes");
        EnumMap<SlaLevel, Integer> copy = new EnumMap<>(SlaLevel.class);
        copy.putAll(targetLatencyMinutes);
        for (SlaLevel level : SlaLevel.values()) {
            if (!copy.containsKey(level)) {
                throw new IllegalArgumentException("missing latency for " + level);
            }
            if (copy.get(level) < 0) {
                throw new IllegalArgumentException("latency must be non-negative for " + level);
            }
        }
        targetLatencyMinutes = Collections.unmodifiableMap(copy);
    }

    public int targetMinutes(SlaLevel level) {
        return targetLatencyMinutes.get(level);
    }

    public static ProcessingSlaPolicy systemDefaults() {
        // Minutes — also used as legacy AI job admission deadline via TenantAiPolicyPort.
        // Long multi-hour transcripts need day-scale windows (see AiJobSla).
        EnumMap<SlaLevel, Integer> latency = new EnumMap<>(SlaLevel.class);
        latency.put(SlaLevel.CRITICAL, 120);   // 2h
        latency.put(SlaLevel.HIGH, 1_440);     // 24h
        latency.put(SlaLevel.NORMAL, 1_440);   // 24h
        latency.put(SlaLevel.BULK, 2_880);     // 48h
        return new ProcessingSlaPolicy(SlaLevel.NORMAL, latency);
    }
}
