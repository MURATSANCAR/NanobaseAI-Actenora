package com.nanobaseai.actenora.modelmanagement.application;

/**
 * Authorization gate for model control-plane operations.
 */
public interface ModelControlPermissionPort {

    void require(ActorPrincipal actor, ModelControlPermission permission);
}
