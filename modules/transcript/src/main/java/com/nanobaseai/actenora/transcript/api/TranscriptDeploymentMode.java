package com.nanobaseai.actenora.transcript.api;

/**
 * Feature-flag constants for embedded vs extracted transcript deployment (FAZ 26).
 *
 * <ul>
 *   <li>{@code embedded} — transcript beans run inside the caller process (monolith default
 *       or dedicated transcript-worker).</li>
 *   <li>{@code remote} — platform-backend disables embedded beans and proxies over HTTP.</li>
 * </ul>
 */
public final class TranscriptDeploymentMode {

    public static final String PROPERTY = "actenora.transcript.mode";
    public static final String EMBEDDED = "embedded";
    public static final String REMOTE = "remote";

    private TranscriptDeploymentMode() {
    }
}
