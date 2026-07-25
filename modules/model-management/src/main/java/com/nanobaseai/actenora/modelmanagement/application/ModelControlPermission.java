package com.nanobaseai.actenora.modelmanagement.application;

/**
 * Permissions for FAZ 11 control-plane APIs.
 */
public enum ModelControlPermission {
    MODEL_REGISTER,
    MODEL_UPDATE,
    MODEL_ENABLE_DISABLE,
    MODEL_DRAIN,
    CAPABILITY_CONFIGURE,
    DEPLOYMENT_REGISTER,
    DEPLOYMENT_HEARTBEAT,
    HEALTH_VIEW
}
