package ai.nanobase.actenora.policy.domain;

/**
 * Processing priority / SLA class for AI and transcript work.
 */
public enum SlaLevel {
    CRITICAL,
    HIGH,
    NORMAL,
    BULK;

    public boolean isAtLeast(SlaLevel other) {
        return this.ordinal() <= other.ordinal();
    }
}
