package com.nanobaseai.actenora.microsoftconnection.application.port;

import com.nanobaseai.actenora.microsoftconnection.application.model.DirectoryUser;

import java.util.Optional;
import java.util.UUID;

/**
 * Microsoft Graph directory (Entra user) lookups.
 */
public interface DirectoryGateway {

    /**
     * Resolve a user by Entra object id. Returns empty when the user is missing
     * or the app lacks directory read permission (403).
     */
    Optional<DirectoryUser> resolveUser(UUID tenantId, String objectId);
}
