package com.nanobaseai.actenora.microsoftconnection.application.port;

import com.nanobaseai.actenora.microsoftconnection.application.model.AccessToken;

/**
 * Acquires Microsoft identity platform access tokens for Graph calls.
 * Certificate-based credentials are the production path; client secret is local/test only.
 */
public interface MicrosoftTokenProvider {

    AccessToken getAccessToken();

    /**
     * Forces a fresh token (e.g. after Graph 401). Implementations must invalidate cache.
     */
    AccessToken refreshAccessToken();
}
