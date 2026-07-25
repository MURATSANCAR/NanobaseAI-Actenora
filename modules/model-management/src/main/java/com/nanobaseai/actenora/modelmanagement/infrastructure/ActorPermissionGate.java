package com.nanobaseai.actenora.modelmanagement.infrastructure;

import com.nanobaseai.actenora.modelmanagement.application.ActorPrincipal;
import com.nanobaseai.actenora.modelmanagement.application.ModelControlPermission;
import com.nanobaseai.actenora.modelmanagement.application.ModelControlPermissionPort;
import com.nanobaseai.actenora.modelmanagement.domain.ModelRegistryException;

/**
 * Default permission check against the actor's granted permission set.
 */
public final class ActorPermissionGate implements ModelControlPermissionPort {

    @Override
    public void require(ActorPrincipal actor, ModelControlPermission permission) {
        if (actor == null || !actor.has(permission)) {
            throw ModelRegistryException.permissionDenied(permission.name());
        }
    }
}
