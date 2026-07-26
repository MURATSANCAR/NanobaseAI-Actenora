package com.nanobaseai.actenora.security.aiprocessing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Multi-model routing settings for the job execution path (FAZ 15).
 */
@ConfigurationProperties(prefix = "actenora.ai.routing")
public class AiRoutingProperties {

    /** When false, the FAZ 12 capability route stays authoritative and no routing audit is written. */
    private boolean enabled = true;

    /** Champion/challenger shadow scheduling. Shadow results never replace the production route. */
    private boolean shadowExecutionEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isShadowExecutionEnabled() {
        return shadowExecutionEnabled;
    }

    public void setShadowExecutionEnabled(boolean shadowExecutionEnabled) {
        this.shadowExecutionEnabled = shadowExecutionEnabled;
    }
}
