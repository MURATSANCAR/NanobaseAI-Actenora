package ai.nanobase.actenora.policy.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps SLA levels to target processing latency (minutes).
 */
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
        EnumMap<SlaLevel, Integer> latency = new EnumMap<>(SlaLevel.class);
        latency.put(SlaLevel.CRITICAL, 5);
        latency.put(SlaLevel.HIGH, 15);
        latency.put(SlaLevel.NORMAL, 60);
        latency.put(SlaLevel.BULK, 240);
        return new ProcessingSlaPolicy(SlaLevel.NORMAL, latency);
    }
}
